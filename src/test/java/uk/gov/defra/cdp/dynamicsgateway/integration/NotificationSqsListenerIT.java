package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.exception.AmqpErrorCondition;
import com.azure.core.amqp.exception.AmqpException;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
class NotificationSqsListenerIT extends IntegrationBase {

    private static final String QUEUE_NAME_SQS = "trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";

    // Deterministic in-process retry budget for the ITs: 3 attempts at 500ms then 1000ms backoff =
    // a 1.5s window that stays well below the 30s visibility timeout, so retries run inside a single
    // SQS receive (no redelivery interleaving), the backoff intervals can be measured cleanly, and
    // an exhausted message stays invisible long enough to assert one processing cycle's attempt count.
    private static final int RETRY_MAX_ATTEMPTS = 3;
    private static final long RETRY_INITIAL_INTERVAL_MS = 500L;
    private static final double RETRY_MULTIPLIER = 2.0;

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.0.2"))
        .withServices(LocalStackContainer.Service.SQS);

    private static String queueUrl;

    static {
        LOCAL_STACK.start();
    }

    @BeforeAll
    static void createQueue() {
        queueUrl = createFifoQueueAndGetUrl();
    }

    @MockitoSpyBean
    private ServiceBusSenderClient senderClient;

    @DynamicPropertySource
    static void setLocalStackProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.sqs.notification.queue-url", () -> queueUrl);
        registry.add("aws.sqs.notification.wait-time-seconds", () -> "1");
        registry.add("aws.sqs.notification.visibility-timeout-seconds", () -> "30");
        registry.add("aws.sqs.notification.retry.max-attempts", () -> String.valueOf(RETRY_MAX_ATTEMPTS));
        registry.add("aws.sqs.notification.retry.initial-interval", () -> String.valueOf(RETRY_INITIAL_INTERVAL_MS));
        registry.add("aws.sqs.notification.retry.multiplier", () -> String.valueOf(RETRY_MULTIPLIER));
        registry.add("aws.sqs.notification.retry.max-interval", () -> "5000");
        // 127.0.0.1:PORT is the resolvable endpoint; LocalStack returns sqs.*.localhost:4566
        // as the queue hostname which cannot be resolved outside the container.
        registry.add("app.aws.endpoint-override",
            () -> LOCAL_STACK.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        registry.add("app.aws.access-key-id", LOCAL_STACK::getAccessKey);
        registry.add("app.aws.secret-access-key", LOCAL_STACK::getSecretKey);
    }

    @BeforeEach
    void purgeQueueAndResetSpy() {
        // Reset before each test as well as after: the exact senderClient invocation-count assertions
        // must start from a clean slate, independent of test ordering or any stray invocation.
        Mockito.reset(senderClient);
        try (SqsClient sqs = localSqsClient()) {
            sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
        } catch (Exception e) {
            log.debug("Queue purge skipped or incomplete before test: {}", e.getMessage());
        }
    }

    @AfterEach
    void resetSpy() {
        Mockito.reset(senderClient);
    }

    @Test
    void sqsToAsb_shouldForwardMessage_whenValidEvent() {
        // Given
        String eventBody = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
        String deduplicationId = UUID.randomUUID().toString();
        sendToSqs(eventBody, AGGREGATE_ID, deduplicationId);

        // When / Then — wait up to 30s for the listener to poll + forward
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<ServiceBusReceivedMessage> received = tryReceiveFromAsb();
            assertThat(received).isPresent();
            assertThat(received.get().getBody()).hasToString(eventBody);
            assertThat(received.get().getRawAmqpMessage().getProperties().getContentType()).isEqualTo("application/json");
            // The inbound SQS MessageDeduplicationId is carried through as the ASB messageId.
            assertThat(received.get().getMessageId()).isEqualTo(deduplicationId);
            assertThat(received.get().getSessionId()).isEqualTo(AGGREGATE_ID);
        });
    }

    @Test
    void sqsToAsb_shouldExhaustRetriesThenLeaveMessageInSqs_whenAsbFailureIsTransient() {
        // Given — ASB always rejects with a transient error; QueueMessageSender wraps it as retryable.
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — the in-process retry exhausts at exactly maxAttempts for the single processing
        // cycle, then the exception propagates so the message is left in SQS for redelivery (→ DLQ after
        // maxReceiveCount). The 30s visibility timeout keeps the message invisible well past the ~1.5s
        // retry window, so the count settles at maxAttempts before any redelivery could re-invoke the sender.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(Mockito.mockingDetails(senderClient).getInvocations())
                .as("a transient failure must be retried in-process up to maxAttempts before giving up")
                .hasSize(RETRY_MAX_ATTEMPTS));
        assertThat(totalMessagesInQueue())
            .as("after exhausting in-process retries the message must remain in SQS for redelivery, not be deleted")
            .isGreaterThanOrEqualTo(1);
    }

    @Test
    void sqsToAsb_shouldForwardWithoutReachingDlq_whenTransientFailureRecoversWithinWindow() {
        // Given — AC2: an intermittent ASB failure that recovers. Fail transiently once, then let the
        // real sender deliver to ASB on the second (in-process) attempt.
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).doCallRealMethod().when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
        String deduplicationId = UUID.randomUUID().toString();
        sendToSqs(eventBody, AGGREGATE_ID, deduplicationId);

        // When / Then — the retry heals it: the event reaches ASB and never lands on the DLQ.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<ServiceBusReceivedMessage> received = tryReceiveFromAsb();
            assertThat(received)
                .as("an intermittent failure that recovers within the window must still publish to ASB")
                .isPresent();
            assertThat(received.get().getBody()).hasToString(eventBody);
            assertThat(received.get().getMessageId()).isEqualTo(deduplicationId);
        });
        assertThat(Mockito.mockingDetails(senderClient).getInvocations())
            .as("recovery should take exactly two attempts: one transient failure then one success")
            .hasSize(2);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(totalMessagesInQueue())
                .as("a recovered message must be deleted from SQS, not left for the DLQ")
                .isZero());
    }

    @Test
    void sqsToAsb_shouldEscalateBackoff_betweenInProcessRetries() {
        // Given — ASB always fails transiently; record the wall-clock time of each publish attempt.
        List<Long> attemptNanos = new CopyOnWriteArrayList<>();
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doAnswer(invocation -> {
            attemptNanos.add(System.nanoTime());
            throw transientEx;
        }).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
        sendToSqs(eventBody, AGGREGATE_ID);

        // When — wait for the in-process retry budget to be exhausted (maxAttempts publishes, one receive).
        await().atMost(Duration.ofSeconds(30)).until(() -> attemptNanos.size() >= RETRY_MAX_ATTEMPTS);

        // Then — gaps between consecutive attempts grow by the configured multiplier (exponential backoff).
        long firstGapMs = millisBetween(attemptNanos.get(0), attemptNanos.get(1));
        long secondGapMs = millisBetween(attemptNanos.get(1), attemptNanos.get(2));
        long expectedFirstMs = RETRY_INITIAL_INTERVAL_MS;
        long expectedSecondMs = (long) (RETRY_INITIAL_INTERVAL_MS * RETRY_MULTIPLIER);
        long toleranceMs = 150; // assert lower bounds only — scheduling jitter can only lengthen a sleep

        assertThat(firstGapMs)
            .as("first backoff should be ~%dms (initial interval)", expectedFirstMs)
            .isGreaterThanOrEqualTo(expectedFirstMs - toleranceMs);
        assertThat(secondGapMs)
            .as("second backoff should be ~%dms (initial interval × multiplier)", expectedSecondMs)
            .isGreaterThanOrEqualTo(expectedSecondMs - toleranceMs);
        assertThat(secondGapMs)
            .as("backoff must escalate: the second gap is longer than the first")
            .isGreaterThan(firstGapMs);
    }

    @Test
    void sqsToAsb_shouldDiscardMessage_whenBodyIsInvalidJson() {
        // Given — invalid JSON that will fail objectMapper.readTree()
        sendToSqs("not valid json {{{", AGGREGATE_ID);

        // When / Then — listener should discard (not retry) the poison message
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(totalMessagesInQueue())
                .as("invalid JSON must be discarded, not left in the queue for retry")
                .isZero());
    }

    @Test
    void sqsToAsb_shouldDiscardMessage_whenAsbFailureIsNonTransient() {
        // Given — ASB rejects with a non-transient error (e.g. entity not found)
        AmqpException nonTransientCause = new AmqpException(false, AmqpErrorCondition.NOT_FOUND, "entity not found", null);
        ServiceBusException nonTransientEx = new ServiceBusException(nonTransientCause, ServiceBusErrorSource.SEND);
        doThrow(nonTransientEx).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — listener must discard (not retry) after a non-transient ASB failure
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(totalMessagesInQueue())
                .as("non-transient ASB failures must discard the message, not retry")
                .isZero());
        assertThat(Mockito.mockingDetails(senderClient).getInvocations())
            .as("non-retryable failures must not be retried — exactly one publish attempt, no backoff")
            .hasSize(1);
    }

    private static long millisBetween(long startNanos, long endNanos) {
        return Duration.ofNanos(endNanos - startNanos).toMillis();
    }

    private int totalMessagesInQueue() {
        try (SqsClient sqs = localSqsClient()) {
            var attributes = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                    .build())
                .attributes();
            int visible = Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
            int inFlight = Integer.parseInt(
                attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
            return visible + inFlight;
        }
    }

    private static String createFifoQueueAndGetUrl() {
        try (SqsClient sqs = localSqsClient()) {
            sqs.createQueue(CreateQueueRequest.builder()
                .queueName(QUEUE_NAME_SQS)
                .attributes(Map.of(
                    QueueAttributeName.FIFO_QUEUE, "true",
                    QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true"))
                .build());
            return sqs.getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(QUEUE_NAME_SQS)
                .build())
                .queueUrl();
        }
    }

    private void sendToSqs(String body, String messageGroupId) {
        sendToSqs(body, messageGroupId, UUID.randomUUID().toString());
    }

    private void sendToSqs(String body, String messageGroupId, String deduplicationId) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageGroupId(messageGroupId)
                .messageDeduplicationId(deduplicationId)
                .build());
        }
    }

    private Optional<ServiceBusReceivedMessage> tryReceiveFromAsb() {
        // sessionReceiver and receiver share a lifecycle: both close when the try block exits.
        try (ServiceBusSessionReceiverClient sessionReceiver = new ServiceBusClientBuilder()
                .connectionString(SERVICE_BUS_CONTAINER.getConnectionString())
                .retryOptions(new AmqpRetryOptions().setTryTimeout(Duration.ofSeconds(3)).setMaxRetries(0))
                .sessionReceiver()
                .queueName(QUEUE_NAME)
                .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
                .buildClient();
             ServiceBusReceiverClient receiver = sessionReceiver.acceptNextSession()) {
            return receiver.receiveMessages(1, Duration.ofSeconds(3))
                .stream()
                .findFirst();
        } catch (Exception e) {
            log.debug("No ASB message received yet: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static SqsClient localSqsClient() {
        return SqsClient.builder()
            .endpointOverride(LOCAL_STACK.getEndpointOverride(LocalStackContainer.Service.SQS))
            .region(Region.of("us-east-1"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
            .build();
    }
}
