package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    private static final String SOURCE_URL = "http://localhost:4566/000000000000/notifications.fifo";
    private static final String DLQ_URL = "http://localhost:4566/000000000000/notifications-deadletter.fifo";
    private static final String GROUP_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final ReceiveMessageResponse EMPTY = ReceiveMessageResponse.builder().build();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DlqService service(String dlqUrl) {
        NotificationSqsConfig config = new NotificationSqsConfig(
            SOURCE_URL, dlqUrl, 30, 20, 10,
            new NotificationSqsConfig.Retry(4, 1000, 2.0, 10000));
        return new DlqService(sqsAsyncClient, config, objectMapper);
    }

    private static Message message(String dedupId, String body, int receiveCount, String receiptHandle) {
        return Message.builder()
            .body(body)
            .receiptHandle(receiptHandle)
            .attributes(Map.of(
                MessageSystemAttributeName.MESSAGE_GROUP_ID, GROUP_ID,
                MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID, dedupId,
                MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, String.valueOf(receiveCount)))
            .build();
    }

    private static ReceiveMessageResponse batch(Message... messages) {
        return ReceiveMessageResponse.builder().messages(messages).build();
    }

    @SuppressWarnings("unchecked")
    private void stubReceive(ReceiveMessageResponse first, ReceiveMessageResponse... rest) {
        var stub = when(sqsAsyncClient.receiveMessage(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(first));
        for (ReceiveMessageResponse response : rest) {
            stub = stub.thenReturn(CompletableFuture.completedFuture(response));
        }
    }

    @SuppressWarnings("unchecked")
    private void stubGetCount(String count) {
        when(sqsAsyncClient.getQueueAttributes(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(GetQueueAttributesResponse.builder()
                .attributes(Map.of(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, count))
                .build()));
    }

    @SuppressWarnings("unchecked")
    private void stubDeleteBatch() {
        when(sqsAsyncClient.deleteMessageBatch(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(DeleteMessageBatchResponse.builder().build()));
    }

    /** Stub sendMessageBatch to report the given entry ids (indices) as successfully re-sent. */
    @SuppressWarnings("unchecked")
    private void stubSendBatch(String... successfulIds) {
        List<SendMessageBatchResultEntry> successful = java.util.Arrays.stream(successfulIds)
            .map(id -> SendMessageBatchResultEntry.builder().id(id).messageId("m-" + id).build())
            .toList();
        when(sqsAsyncClient.sendMessageBatch(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(
                SendMessageBatchResponse.builder().successful(successful).build()));
    }

    @SuppressWarnings("unchecked")
    private void stubChangeVisibilityBatch() {
        when(sqsAsyncClient.changeMessageVisibilityBatch(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(
                ChangeMessageVisibilityBatchResponse.builder().build()));
    }

    @SuppressWarnings("unchecked")
    private SendMessageBatchRequest captureSendBatch() {
        ArgumentCaptor<Consumer<SendMessageBatchRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsAsyncClient).sendMessageBatch(captor.capture());
        SendMessageBatchRequest.Builder builder = SendMessageBatchRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private DeleteMessageBatchRequest captureDeleteBatch() {
        ArgumentCaptor<Consumer<DeleteMessageBatchRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsAsyncClient).deleteMessageBatch(captor.capture());
        DeleteMessageBatchRequest.Builder builder = DeleteMessageBatchRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private ChangeMessageVisibilityBatchRequest captureVisibilityBatch() {
        ArgumentCaptor<Consumer<ChangeMessageVisibilityBatchRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsAsyncClient).changeMessageVisibilityBatch(captor.capture());
        ChangeMessageVisibilityBatchRequest.Builder builder = ChangeMessageVisibilityBatchRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    @Test
    void list_projectsMessagesToDtosWithApproximateCount() {
        stubReceive(batch(
            message("dedup-1", "{\"key\":\"a\"}", 3, "rh-1"),
            message("dedup-2", "{\"key\":\"b\"}", 4, "rh-2")), EMPTY);
        stubGetCount("7");
        stubChangeVisibilityBatch();

        DlqListResponse response = service(DLQ_URL).list(10);

        assertThat(response.approximateCount()).isEqualTo(7L);
        assertThat(response.messages()).hasSize(2);
        DlqMessage first = response.messages().getFirst();
        assertThat(first.id()).isEqualTo("dedup-1");
        assertThat(first.messageGroupId()).isEqualTo(GROUP_ID);
        assertThat(first.deduplicationId()).isEqualTo("dedup-1");
        assertThat(first.approximateReceiveCount()).isEqualTo(3);
        assertThat(first.body()).isEqualTo("{\"key\":\"a\"}");
    }

    @Test
    void list_usesEventIdFromBodyAsId_whenPresent() {
        stubReceive(batch(message("dedup-1", "{\"eventId\":\"evt-99\"}", 3, "rh-1")), EMPTY);
        stubGetCount("1");
        stubChangeVisibilityBatch();

        DlqListResponse response = service(DLQ_URL).list(10);

        assertThat(response.messages().getFirst().id()).isEqualTo("evt-99");
        assertThat(response.messages().getFirst().deduplicationId()).isEqualTo("dedup-1");
    }

    @Test
    void list_pagesAcrossReceivesUpToLimit_andReleasesBrowsedInOneBatch() {
        stubReceive(
            batch(message("dedup-1", "{\"key\":\"a\"}", 3, "rh-1"),
                  message("dedup-2", "{\"key\":\"b\"}", 3, "rh-2")),
            batch(message("dedup-3", "{\"key\":\"c\"}", 3, "rh-3")),
            EMPTY);
        stubGetCount("3");
        stubChangeVisibilityBatch();

        DlqListResponse response = service(DLQ_URL).list(50);

        assertThat(response.messages())
            .extracting(DlqMessage::id)
            .containsExactly("dedup-1", "dedup-2", "dedup-3");
        // Non-destructive: all three browsed messages released in a single batch, none deleted.
        assertThat(captureVisibilityBatch().entries()).hasSize(3);
        verify(sqsAsyncClient, never()).deleteMessageBatch(any(Consumer.class));
    }

    @Test
    void replay_reSendsBatchToSourcePreservingGroupAndDedupId_thenDeletesFromDlq() {
        stubReceive(batch(message("dedup-1", "{\"key\":\"a\"}", 3, "rh-1")));
        stubSendBatch("0");
        stubDeleteBatch();

        service(DLQ_URL).replay(List.of("dedup-1"));

        SendMessageBatchRequest sent = captureSendBatch();
        assertThat(sent.queueUrl()).isEqualTo(SOURCE_URL);
        assertThat(sent.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.messageGroupId()).isEqualTo(GROUP_ID);
            assertThat(entry.messageDeduplicationId()).isEqualTo("dedup-1");
            assertThat(entry.messageBody()).isEqualTo("{\"key\":\"a\"}");
        });

        DeleteMessageBatchRequest deleted = captureDeleteBatch();
        assertThat(deleted.queueUrl()).isEqualTo(DLQ_URL);
        assertThat(deleted.entries()).singleElement()
            .satisfies(entry -> assertThat(entry.receiptHandle()).isEqualTo("rh-1"));
    }

    @Test
    void replay_usesEventIdAsSourceDedupId_whenBodyIsEnveloped() {
        stubReceive(batch(message("dedup-1", "{\"eventId\":\"evt-77\"}", 3, "rh-1")));
        stubSendBatch("0");
        stubDeleteBatch();

        service(DLQ_URL).replay(List.of("evt-77"));

        assertThat(captureSendBatch().entries()).singleElement()
            .satisfies(entry -> assertThat(entry.messageDeduplicationId()).isEqualTo("evt-77"));
    }

    @Test
    void replay_onlyActionsRequestedIds_andReleasesUnrequestedMessages() {
        stubReceive(
            batch(message("dedup-1", "{\"key\":\"a\"}", 3, "rh-1"),
                  message("dedup-2", "{\"key\":\"b\"}", 3, "rh-2")),
            EMPTY);
        stubSendBatch("0");
        stubDeleteBatch();
        stubChangeVisibilityBatch();

        service(DLQ_URL).replay(List.of("dedup-1", "missing"));

        // Only dedup-1 re-sent + deleted; the unrequested dedup-2 released, not replayed.
        assertThat(captureSendBatch().entries()).singleElement()
            .satisfies(entry -> assertThat(entry.messageBody()).isEqualTo("{\"key\":\"a\"}"));
        assertThat(captureDeleteBatch().entries()).singleElement()
            .satisfies(entry -> assertThat(entry.receiptHandle()).isEqualTo("rh-1"));
        assertThat(captureVisibilityBatch().entries()).singleElement()
            .satisfies(entry -> assertThat(entry.receiptHandle()).isEqualTo("rh-2"));
    }

    @Test
    void delete_removesRequestedByReceiptHandle_releasesOthers_neverSends() {
        stubReceive(batch(
            message("dedup-1", "{\"key\":\"a\"}", 3, "rh-1"),
            message("dedup-2", "{\"key\":\"b\"}", 3, "rh-2")));
        stubDeleteBatch();
        stubChangeVisibilityBatch();

        service(DLQ_URL).delete(List.of("dedup-2"));

        assertThat(captureDeleteBatch().entries()).singleElement()
            .satisfies(entry -> assertThat(entry.receiptHandle()).isEqualTo("rh-2"));
        assertThat(captureVisibilityBatch().entries()).singleElement()
            .satisfies(entry -> assertThat(entry.receiptHandle()).isEqualTo("rh-1"));
        verify(sqsAsyncClient, never()).sendMessageBatch(any(Consumer.class));
        verify(sqsAsyncClient, never()).purgeQueue(any(Consumer.class));
    }

    @Test
    void delete_doesNothing_whenIdNeverSeen() {
        stubReceive(batch(message("dedup-1", "{\"key\":\"a\"}", 3, "rh-1")), EMPTY);
        stubChangeVisibilityBatch();

        service(DLQ_URL).delete(List.of("missing"));

        verify(sqsAsyncClient, never()).deleteMessageBatch(any(Consumer.class));
        // The unrequested message it scanned past is released once (the second poll is empty).
        verify(sqsAsyncClient, times(1)).changeMessageVisibilityBatch(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void replay_deletesOnlyReSentEntries_whenSendBatchPartiallyFails() {
        stubReceive(batch(
            message("dedup-1", "{\"key\":\"a\"}", 3, "rh-1"),
            message("dedup-2", "{\"key\":\"b\"}", 3, "rh-2")));
        // Entry 0 re-sends; entry 1 fails — only the re-sent one may be deleted from the DLQ.
        when(sqsAsyncClient.sendMessageBatch(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(SendMessageBatchResponse.builder()
                .successful(SendMessageBatchResultEntry.builder().id("0").messageId("m-0").build())
                .failed(BatchResultErrorEntry.builder().id("1").message("boom").senderFault(false).build())
                .build()));
        stubDeleteBatch();

        service(DLQ_URL).replay(List.of("dedup-1", "dedup-2"));

        assertThat(captureDeleteBatch().entries()).singleElement()
            .satisfies(entry -> assertThat(entry.receiptHandle()).isEqualTo("rh-1"));
    }

    @Test
    void list_throws_whenDlqUrlNotConfigured() {
        DlqService service = service("");
        assertThatThrownBy(() -> service.list(10))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DLQ URL is not configured");
    }
}
