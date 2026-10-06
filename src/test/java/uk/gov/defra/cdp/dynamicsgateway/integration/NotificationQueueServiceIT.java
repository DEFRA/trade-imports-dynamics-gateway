package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.floci.testcontainers.FlociContainer;
import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;
import uk.gov.defra.cdp.dynamicsgateway.notification.NotificationQueueDepth;
import uk.gov.defra.cdp.dynamicsgateway.notification.NotificationQueueService;

/**
 * Exercises {@link NotificationQueueService} against a real SQS (Floci) so the visible and in-flight
 * counts come from the actual {@code GetQueueAttributes} API rather than mocks. Standalone (no Spring
 * context, no ASB emulator).
 */
@Slf4j
class NotificationQueueServiceIT {

    private static final String SOURCE_QUEUE = "trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String GROUP_PREFIX = "Imports.Notification.GBN-AG.GBN-AG-26-";
    private static final String REGION = "us-east-1";
    private static final int IN_FLIGHT_VISIBILITY_TIMEOUT_SECONDS = 60;

    private static final FlociContainer FLOCI = new FlociContainer(
        DockerImageName.parse("floci/floci:latest"))
        .withRegion(REGION);

    private static String sourceUrl;
    private static SqsAsyncClient asyncSqsClient;

    static {
        FLOCI.start();
    }

    private NotificationQueueService queueService;

    @BeforeAll
    static void createQueue() {
        asyncSqsClient = buildAsyncSqsClient();
        try (SqsClient sqs = localSqsClient()) {
            sourceUrl = createFifoQueue(sqs, SOURCE_QUEUE);
        }
    }

    @AfterAll
    static void closeAsyncClient() {
        asyncSqsClient.close();
    }

    @BeforeEach
    void setUp() {
        purge(sourceUrl);
        await().atMost(Duration.ofSeconds(30)).until(() -> totalInQueue(sourceUrl) == 0);
        NotificationSqsConfig config = new NotificationSqsConfig(
            sourceUrl, "http://localhost/unused-dlq", "arn:aws:sqs:us-east-1:000000000000:unused", 20, 10);
        queueService = new NotificationQueueService(asyncSqsClient, config);
    }

    @Test
    void depth_shouldCountVisibleMessages() {
        // Given
        for (int i = 0; i < 3; i++) {
            send("{\"key\":\"" + i + "\"}", GROUP_PREFIX + i, "dedup-" + i);
        }

        // When / Then
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            NotificationQueueDepth depth = queueService.depth();
            assertThat(depth.approximateCount()).isEqualTo(3L);
            assertThat(depth.approximateInFlightCount()).isZero();
        });
    }

    @Test
    void depth_shouldCountInFlightMessagesSeparately() {
        // Given
        send("{\"key\":\"a\"}", GROUP_PREFIX + "a", "dedup-a");
        send("{\"key\":\"b\"}", GROUP_PREFIX + "b", "dedup-b");
        await().atMost(Duration.ofSeconds(15)).until(() -> totalInQueue(sourceUrl) == 2);

        // When
        receiveWithoutDeleting(sourceUrl);

        // Then
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            NotificationQueueDepth depth = queueService.depth();
            assertThat(depth.approximateCount()).isEqualTo(1L);
            assertThat(depth.approximateInFlightCount()).isEqualTo(1L);
        });
    }

    private void receiveWithoutDeleting(String url) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(url)
                .maxNumberOfMessages(1)
                .visibilityTimeout(IN_FLIGHT_VISIBILITY_TIMEOUT_SECONDS)
                .waitTimeSeconds(1)
                .build());
        }
    }

    private void send(String body, String group, String dedupId) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(sourceUrl)
                .messageBody(body)
                .messageGroupId(group)
                .messageDeduplicationId(dedupId)
                .build());
        }
    }

    private int totalInQueue(String queueUrl) {
        try (SqsClient sqs = localSqsClient()) {
            var attributes = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                    .build())
                .attributes();
            return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
        }
    }

    private void purge(String queueUrl) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
        } catch (Exception e) {
            log.debug("Queue purge skipped: {}", e.getMessage());
        }
    }

    private static String createFifoQueue(SqsClient sqs, String name) {
        Map<QueueAttributeName, String> attributes = new EnumMap<>(Map.of(
            QueueAttributeName.FIFO_QUEUE, "true",
            QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false"));
        sqs.createQueue(CreateQueueRequest.builder()
            .queueName(name)
            .attributes(attributes)
            .build());
        return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
    }

    private static SqsClient localSqsClient() {
        return SqsClient.builder()
            .endpointOverride(URI.create(FLOCI.getEndpoint()))
            .region(Region.of(REGION))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(FLOCI.getAccessKey(), FLOCI.getSecretKey())))
            .build();
    }

    private static SqsAsyncClient buildAsyncSqsClient() {
        return SqsAsyncClient.builder()
            .endpointOverride(URI.create(FLOCI.getEndpoint()))
            .region(Region.of(REGION))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(FLOCI.getAccessKey(), FLOCI.getSecretKey())))
            .build();
    }
}
