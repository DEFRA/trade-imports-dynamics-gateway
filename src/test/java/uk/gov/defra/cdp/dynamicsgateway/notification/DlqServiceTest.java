package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.PurgeQueueInProgressException;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskRequest;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskResponse;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    private static final String SOURCE_URL = "http://localhost:4566/000000000000/notifications.fifo";
    private static final String DLQ_URL = "http://localhost:4566/000000000000/notifications-deadletter.fifo";
    private static final String DLQ_ARN = "arn:aws:sqs:eu-west-2:332499610595:notifications-deadletter.fifo";
    private static final String GROUP_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final ReceiveMessageResponse EMPTY = ReceiveMessageResponse.builder().build();

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DlqService service(String dlqUrl) {
        NotificationSqsConfig config = new NotificationSqsConfig(SOURCE_URL, dlqUrl, DLQ_ARN, 20, 10);
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
    private void stubChangeVisibilityBatch() {
        when(sqsAsyncClient.changeMessageVisibilityBatch(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(
                ChangeMessageVisibilityBatchResponse.builder().build()));
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
    void replayAll_startsMoveTask_sourcedFromConfiguredDlqArn() {
        when(sqsAsyncClient.startMessageMoveTask(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(StartMessageMoveTaskResponse.builder().build()));

        service(DLQ_URL).replayAll();

        ArgumentCaptor<Consumer<StartMessageMoveTaskRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsAsyncClient).startMessageMoveTask(captor.capture());
        StartMessageMoveTaskRequest.Builder builder = StartMessageMoveTaskRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().sourceArn()).isEqualTo(DLQ_ARN);
    }

    @Test
    void replayAll_throwsCompletionException_whenAnotherMoveTaskIsAlreadyRunning() {
        SqsException limitExceeded = (SqsException) SqsException.builder()
            .message("A message move task is already in progress")
            .awsErrorDetails(AwsErrorDetails.builder()
                .errorCode("AWS.SimpleQueueService.MessageMoveTask.LimitExceeded")
                .build())
            .build();
        when(sqsAsyncClient.startMessageMoveTask(any(Consumer.class)))
            .thenReturn(CompletableFuture.failedFuture(limitExceeded));

        // join() wraps the SDK exception in a CompletionException; GlobalExceptionHandler unwraps it.
        DlqService service = service(DLQ_URL);
        assertThatThrownBy(service::replayAll)
            .isInstanceOf(CompletionException.class)
            .cause().isSameAs(limitExceeded);
    }

    @Test
    void deleteAll_purgesQueue() {
        when(sqsAsyncClient.purgeQueue(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(PurgeQueueResponse.builder().build()));

        service(DLQ_URL).deleteAll();

        ArgumentCaptor<Consumer<PurgeQueueRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsAsyncClient).purgeQueue(captor.capture());
        PurgeQueueRequest.Builder builder = PurgeQueueRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().queueUrl()).isEqualTo(DLQ_URL);
    }

    @Test
    void deleteAll_throwsCompletionException_whenPurgeAlreadyInProgress() {
        PurgeQueueInProgressException alreadyPurging = PurgeQueueInProgressException.builder()
            .message("Purge already in progress")
            .build();
        when(sqsAsyncClient.purgeQueue(any(Consumer.class)))
            .thenReturn(CompletableFuture.failedFuture(alreadyPurging));

        DlqService service = service(DLQ_URL);
        assertThatThrownBy(service::deleteAll)
            .isInstanceOf(CompletionException.class)
            .cause().isSameAs(alreadyPurging);
    }
}
