package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
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
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
class NotificationSqsListenerIT extends IntegrationBase {

    private static final String QUEUE_NAME_SQS = "trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";

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
        registry.add("aws.sqs.notification.visibility-timeout-seconds", () -> "2");
        // 127.0.0.1:PORT is the resolvable endpoint; LocalStack returns sqs.*.localhost:4566
        // as the queue hostname which cannot be resolved outside the container.
        registry.add("app.aws.endpoint-override",
            () -> LOCAL_STACK.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        registry.add("app.aws.access-key-id", LOCAL_STACK::getAccessKey);
        registry.add("app.aws.secret-access-key", LOCAL_STACK::getSecretKey);
    }

    @BeforeEach
    void purgeQueue() {
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
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — wait up to 30s for the listener to poll + forward
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<ServiceBusReceivedMessage> received = tryReceiveFromAsb();
            assertThat(received).isPresent();
            assertThat(received.get().getBody()).hasToString(eventBody);
            assertThat(received.get().getRawAmqpMessage().getProperties().getContentType()).isEqualTo("application/json");
            assertThat(received.get().getMessageId()).isNotBlank();
            assertThat(received.get().getSessionId()).isEqualTo(AGGREGATE_ID);
        });
    }

    @Test
    void sqsToAsb_shouldLeaveMessageInSqs_whenAsbFailureIsTransient() {
        // Given — ASB rejects with a transient error; QueueMessageSender wraps it as retryable.
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — listener must retry (not discard) after a transient ASB failure
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(Mockito.mockingDetails(senderClient).getInvocations())
                .as("transient ASB failures must be retried, not discarded after the first attempt")
                .hasSizeGreaterThanOrEqualTo(2);
            assertThat(totalMessagesInQueue())
                .as("transient ASB failures must leave the SQS message in the queue")
                .isGreaterThanOrEqualTo(1);
        });
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
        try (SqsClient sqs = localSqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageGroupId(messageGroupId)
                .messageDeduplicationId(UUID.randomUUID().toString())
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
