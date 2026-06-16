package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class NotificationSqsListenerIT extends IntegrationBase {

    private static final String QUEUE_NAME_SQS = "trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.0.2"))
        .withServices(LocalStackContainer.Service.SQS);

    private static String queueUrl;

    static {
        LOCAL_STACK.start();
        queueUrl = createFifoQueueAndGetUrl();
    }

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

    @Test
    void listener_shouldForwardSqsMessageToAzureServiceBus() {
        // Given
        String eventBody = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — wait up to 30s for the listener to poll + forward
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<ServiceBusReceivedMessage> received = tryReceiveFromAsb();
            assertThat(received).isPresent();
            assertThat(received.get().getBody().toString()).isEqualTo(eventBody);
            assertThat(received.get().getSessionId()).isEqualTo(AGGREGATE_ID);
        });
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
