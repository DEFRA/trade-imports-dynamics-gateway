package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
class NotificationSqsListenerIT extends IntegrationBase {

    private static final String QUEUE_NAME_SQS = "trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String DLQ_NAME_SQS = "trade_imports_animals_eu_notifications_gateway-deadletter.fifo";
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    // Mirrors the CDP-provisioned queues: after this many failed receives SQS redrives to the DLQ.
    private static final int MAX_RECEIVE_COUNT = 3;
    // Short visibility timeout so each redelivery cycle — and the redrive to the DLQ after
    // exhaustion — completes in ~2s rather than the 30s SQS default, keeping the redrive IT fast.
    private static final int VISIBILITY_TIMEOUT_SECONDS = 2;

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.0.2"))
        .withServices(LocalStackContainer.Service.SQS);

    private static String queueUrl;
    private static String dlqUrl;

    static {
        LOCAL_STACK.start();
    }

    @BeforeAll
    static void createQueues() {
        try (SqsClient sqs = localSqsClient()) {
            dlqUrl = createFifoQueue(sqs, DLQ_NAME_SQS, Map.of());
            String dlqArn = queueArn(sqs, dlqUrl);
            // RedrivePolicy on the source queue: SQS moves a message to the DLQ once it has been
            // received MAX_RECEIVE_COUNT times without being deleted — the native mechanism this IT
            // exercises. A short VISIBILITY_TIMEOUT_SECONDS keeps each redelivery cycle to ~2s so
            // exhaustion reaches the DLQ in a few seconds; the discard/transient tests ack or assert
            // within the first cycle, so the short timeout doesn't disturb them.
            queueUrl = createFifoQueue(sqs, QUEUE_NAME_SQS, Map.of(
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"" + MAX_RECEIVE_COUNT + "\"}",
                QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(VISIBILITY_TIMEOUT_SECONDS)));
        }
    }

    @MockitoSpyBean
    private ServiceBusSenderClient senderClient;

    @DynamicPropertySource
    static void setLocalStackProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.sqs.notification.queue-url", () -> queueUrl);
        registry.add("aws.sqs.notification.wait-time-seconds", () -> "1");
        // 127.0.0.1:PORT is the resolvable endpoint; LocalStack returns sqs.*.localhost:4566
        // as the queue hostname which cannot be resolved outside the container.
        registry.add("app.aws.endpoint-override",
            () -> LOCAL_STACK.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        registry.add("app.aws.access-key-id", LOCAL_STACK::getAccessKey);
        registry.add("app.aws.secret-access-key", LOCAL_STACK::getSecretKey);
    }

    @BeforeEach
    void purgeQueuesAndResetSpy() {
        // Reset before each test as well as after: the exact senderClient invocation-count assertions
        // must start from a clean slate, independent of test ordering or any stray invocation.
        Mockito.reset(senderClient);
        // Purge the DLQ too so a message redriven by one test can't leak into another's assertions.
        purgeQueue(queueUrl);
        purgeQueue(dlqUrl);
    }

    @AfterEach
    void resetSpy() {
        Mockito.reset(senderClient);
    }

    @Test
    void sqsToAsb_shouldForwardMessage_whenValidEvent() {
        // Given
        String eventBody = notificationJson(AGGREGATE_ID);
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
    void sqsToAsb_shouldLeaveMessageInSqs_whenAsbFailureIsTransient() {
        // Given — ASB always rejects with a transient error; QueueMessageSender wraps it as retryable.
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = notificationJson(AGGREGATE_ID);
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — no in-process retry: a single publish attempt, then the exception propagates so
        // the message is left in SQS for native redelivery (→ DLQ after maxReceiveCount).
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            verify(senderClient, times(1)).sendMessage(any(ServiceBusMessage.class)));
        // ApproximateNumberOfMessages(NotVisible) are eventually-consistent, so poll rather than read once.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(totalMessagesInQueue())
                .as("after a transient failure the message must remain in SQS for redelivery, not be deleted")
                .isEqualTo(1));
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

        String eventBody = notificationJson(AGGREGATE_ID);
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — listener must discard (not retry) after a non-transient ASB failure
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(totalMessagesInQueue())
                .as("non-transient ASB failures must discard the message, not retry")
                .isZero());
        verify(senderClient, times(1)).sendMessage(any(ServiceBusMessage.class));
    }

    @Test
    void sqsToAsb_shouldLandMessageOnDlq_whenAsbFailsUntilRedeliveryIsExhausted() {
        // Given — ASB rejects every attempt transiently, so the error handler re-throws (never acks)
        // and the message is left for native SQS redelivery on each cycle rather than deleted.
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = notificationJson(AGGREGATE_ID);
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — after MAX_RECEIVE_COUNT failed receives the RedrivePolicy moves the message to
        // the DLQ. With the ~2s visibility timeout, exhaustion completes in a few seconds. This proves
        // exhaustion actually reaches the DLQ — no other test covers it; the transient test above only
        // proves the message is *left* on the source queue.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(totalMessagesInQueue(dlqUrl))
                .as("after maxReceiveCount redelivery failures the message must be redriven to the DLQ")
                .isEqualTo(1));

        // The redriven message carries the original event body, not a mutated or empty payload.
        assertThat(receiveOneFromDlq())
            .as("the DLQ must hold the original event body")
            .contains(eventBody);
        // ASB was genuinely attempted on each redelivery before exhaustion, not short-circuited.
        verify(senderClient, atLeast(MAX_RECEIVE_COUNT)).sendMessage(any(ServiceBusMessage.class));
    }

    private static String notificationJson(String aggregateId) {
        return "{\"aggregateId\":\"" + aggregateId + "\",\"eventType\":\"NotificationSubmitted\"}";
    }

    private int totalMessagesInQueue() {
        return totalMessagesInQueue(queueUrl);
    }

    private int totalMessagesInQueue(String url) {
        try (SqsClient sqs = localSqsClient()) {
            var attributes = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(url)
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

    private Optional<String> receiveOneFromDlq() {
        try (SqsClient sqs = localSqsClient()) {
            return sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(1)
                    .build())
                .messages().stream()
                .findFirst()
                .map(Message::body);
        }
    }

    private void purgeQueue(String url) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(url).build());
        } catch (Exception e) {
            log.debug("Queue purge skipped or incomplete before test: {}", e.getMessage());
        }
    }

    private static String createFifoQueue(SqsClient sqs, String name, Map<QueueAttributeName, String> extraAttributes) {
        Map<QueueAttributeName, String> attributes = new EnumMap<>(Map.of(
            QueueAttributeName.FIFO_QUEUE, "true",
            QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true"));
        attributes.putAll(extraAttributes);
        sqs.createQueue(CreateQueueRequest.builder()
            .queueName(name)
            .attributes(attributes)
            .build());
        return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
    }

    private static String queueArn(SqsClient sqs, String queueUrl) {
        return sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build())
            .attributes()
            .get(QueueAttributeName.QUEUE_ARN);
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
