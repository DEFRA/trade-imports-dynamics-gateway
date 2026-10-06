package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SqsException;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;

@ExtendWith(MockitoExtension.class)
class NotificationQueueServiceTest {

    private static final String SOURCE_URL = "http://localhost:4566/000000000000/notifications.fifo";
    private static final String DLQ_URL = "http://localhost:4566/000000000000/notifications-deadletter.fifo";
    private static final String DLQ_ARN = "arn:aws:sqs:eu-west-2:332499610595:notifications-deadletter.fifo";

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private NotificationQueueService service() {
        NotificationSqsConfig config = new NotificationSqsConfig(SOURCE_URL, DLQ_URL, DLQ_ARN, 20, 10);
        return new NotificationQueueService(sqsAsyncClient, config);
    }

    @SuppressWarnings("unchecked")
    private void stubAttributes(Map<QueueAttributeName, String> attributes) {
        when(sqsAsyncClient.getQueueAttributes(any(Consumer.class)))
            .thenReturn(CompletableFuture.completedFuture(
                GetQueueAttributesResponse.builder().attributes(attributes).build()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void depth_shouldReadVisibleAndInFlightCounts() {
        // Given
        stubAttributes(Map.of(
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "7",
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "3"));

        // When
        NotificationQueueDepth depth = service().depth();

        // Then
        assertThat(depth.approximateCount()).isEqualTo(7L);
        assertThat(depth.approximateInFlightCount()).isEqualTo(3L);
        ArgumentCaptor<Consumer<GetQueueAttributesRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqsAsyncClient).getQueueAttributes(captor.capture());
        GetQueueAttributesRequest.Builder builder = GetQueueAttributesRequest.builder();
        captor.getValue().accept(builder);
        GetQueueAttributesRequest request = builder.build();
        assertThat(request.queueUrl()).isEqualTo(SOURCE_URL);
        assertThat(request.attributeNames()).containsExactlyInAnyOrder(
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE);
    }

    @Test
    void depth_shouldGiveZero_whenAnAttributeIsMissing() {
        // Given
        stubAttributes(Map.of(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "4"));

        // When
        NotificationQueueDepth depth = service().depth();

        // Then
        assertThat(depth.approximateCount()).isEqualTo(4L);
        assertThat(depth.approximateInFlightCount()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void depth_shouldThrowCompletionException_whenSqsFails() {
        // Given
        SqsException failure = (SqsException) SqsException.builder().message("queue unavailable").build();
        when(sqsAsyncClient.getQueueAttributes(any(Consumer.class)))
            .thenReturn(CompletableFuture.failedFuture(failure));

        // When / Then
        NotificationQueueService service = service();
        assertThatThrownBy(service::depth)
            .isInstanceOf(CompletionException.class)
            .cause().isSameAs(failure);
    }
}
