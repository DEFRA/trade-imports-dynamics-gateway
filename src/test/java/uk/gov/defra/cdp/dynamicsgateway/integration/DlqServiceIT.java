package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import software.amazon.awssdk.services.sqs.model.ListMessageMoveTasksRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;
import uk.gov.defra.cdp.dynamicsgateway.notification.DlqListResponse;
import uk.gov.defra.cdp.dynamicsgateway.notification.DlqService;

/**
 * Exercises {@link DlqService} against a real SQS (Floci) so the DLQ list/peek plumbing — FIFO
 * receive semantics, receipt-handle lifecycle, system attributes, {@code GetQueueAttributes} counting
 * and the early visibility-release — runs against the actual API rather than mocks. Standalone (no
 * Spring context, no ASB emulator).
 */
@Slf4j
class DlqServiceIT {

    private static final String SOURCE_QUEUE = "trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String DLQ_QUEUE = "trade_imports_animals_eu_notifications_gateway-deadletter.fifo";
    private static final String GROUP_A = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final String GROUP_B = "Imports.Notification.GBN-AG.GBN-AG-26-002";
    private static final String REGION = "us-east-1";
    private static final int MAX_RECEIVE_COUNT = 3;
    // Short source visibility so redrive-seeding (receive-without-deleting past maxReceiveCount)
    // completes in seconds rather than the 30s SQS default.
    private static final int SOURCE_VISIBILITY_TIMEOUT_SECONDS = 1;

    // DlqService reads FIFO system attributes (message group / dedup id) on receive, so the emulator
    // must honour the MessageSystemAttributeNames request param the way production SQS does — Floci does.
    private static final FlociContainer FLOCI = new FlociContainer(
        DockerImageName.parse("floci/floci:latest"))
        .withRegion(REGION);

    private static String sourceUrl;
    private static String dlqUrl;
    private static String dlqArn;
    private static SqsAsyncClient asyncSqsClient;

    static {
        FLOCI.start();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DlqService dlqService;

    @BeforeAll
    static void createQueues() {
        asyncSqsClient = buildAsyncSqsClient();
        try (SqsClient sqs = localSqsClient()) {
            dlqUrl = createFifoQueue(sqs, DLQ_QUEUE, Map.of());
            dlqArn = queueArn(sqs, dlqUrl);
            // StartMessageMoveTask rejects a queue with "Source queue must be configured as a Dead
            // Letter Queue" unless some source queue's RedrivePolicy names it as the DLQ target —
            // this is how AWS (and Floci) resolve the implicit "move back to source" destination.
            // A short visibility timeout lets a message redrive here in seconds during seeding.
            sourceUrl = createFifoQueue(sqs, SOURCE_QUEUE, Map.of(
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"" + MAX_RECEIVE_COUNT + "\"}",
                QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(SOURCE_VISIBILITY_TIMEOUT_SECONDS)));
        }
    }

    @AfterAll
    static void closeAsyncClient() {
        asyncSqsClient.close();
    }

    @BeforeEach
    void setUp() {
        // Clear both queues: replayAll moves messages back onto the source, so a prior test can
        // leave one there.
        purge(sourceUrl);
        purge(dlqUrl);
        // PurgeQueue is async (up to 60s per the AWS API reference), so await drain to 0 before
        // seeding — otherwise a still-draining purge from the prior test can race this test's seed.
        await().atMost(Duration.ofSeconds(30)).until(() -> totalInQueue(dlqUrl) == 0 && totalInQueue(sourceUrl) == 0);
        // DlqService never reads the source-queue URL (list uses dlqUrl, replayAll uses dlqArn), so a
        // placeholder satisfies @NotBlank; the real source queue is driven directly via seedDlqViaRedrive.
        NotificationSqsConfig config = new NotificationSqsConfig(
            "http://localhost/unused-source-queue", dlqUrl, dlqArn, 20, 10);
        dlqService = new DlqService(asyncSqsClient, config, objectMapper);
    }

    @Test
    void list_returnsSeededMessagesWithAttributesAndCount() {
        // Given
        seedDlq("{\"key\":\"a\"}", GROUP_A, "dedup-a");
        seedDlq("{\"key\":\"b\"}", GROUP_B, "dedup-b");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 2);

        // When
        DlqListResponse response = dlqService.list(10);

        // Then
        assertThat(response.approximateCount()).isGreaterThanOrEqualTo(1L);
        assertThat(response.messages())
            .hasSize(2)
            .allSatisfy(message -> {
                assertThat(message.id()).isIn("dedup-a", "dedup-b");
                assertThat(message.messageGroupId()).isIn(GROUP_A, GROUP_B);
                assertThat(message.approximateReceiveCount()).isGreaterThanOrEqualTo(1);
            });
    }

    @Test
    void list_pagesBeyondTenMessages_nonDestructively() {
        // Given
        // 13 > the 10-per-receive SQS cap, so list must page; distinct groups avoid FIFO blocking.
        for (int i = 0; i < 13; i++) {
            seedDlq("{\"key\":\"" + i + "\"}", GROUP_A + "-" + i, "dedup-" + i);
        }
        await().atMost(Duration.ofSeconds(15)).until(() -> totalInQueue(dlqUrl) == 13);

        // When / Then
        assertThat(dlqService.list(50).messages()).hasSize(13);

        // Non-destructive: every browsed message is released back, so the depth is unchanged.
        await().atMost(Duration.ofSeconds(15)).until(() -> totalInQueue(dlqUrl) == 13);
    }

    @Test
    void list_honoursLimitSmallerThanQueueDepth() {
        // Given
        for (int i = 0; i < 5; i++) {
            seedDlq("{\"key\":\"" + i + "\"}", GROUP_A + "-" + i, "dedup-" + i);
        }
        await().atMost(Duration.ofSeconds(15)).until(() -> totalInQueue(dlqUrl) == 5);

        // When / Then
        assertThat(dlqService.list(3).messages()).hasSize(3);
    }

    @Test
    void deleteAll_purgesTheRealQueue() {
        // Given
        seedDlq("{\"key\":\"a\"}", GROUP_A, "dedup-a");
        seedDlq("{\"key\":\"b\"}", GROUP_B, "dedup-b");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 2);

        // When
        dlqService.deleteAll();

        // Then
        // PurgeQueue is async — per the AWS API reference, deletion can take up to 60s to complete.
        await().atMost(Duration.ofSeconds(30)).until(() -> totalInQueue(dlqUrl) == 0);
    }

    @Test
    void replayAll_drainsTheDlqBackOntoTheSource() {
        // Given — a message that reached the DLQ the real way: redriven from the source after
        // exhausting maxReceiveCount. Only then does it carry the original-source provenance that
        // replayAll's destination-less StartMessageMoveTask needs to move it back (a directly-seeded
        // message has no source and would never leave the DLQ).
        seedDlqViaRedrive("{\"key\":\"a\"}", GROUP_A, "dedup-a");

        // When
        dlqService.replayAll();

        // Then — the move task is registered...
        try (SqsClient sqs = localSqsClient()) {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(sqs.listMessageMoveTasks(ListMessageMoveTasksRequest.builder()
                        .sourceArn(dlqArn)
                        .build())
                    .results())
                    .isNotEmpty());
        }
        // ...and it actually finishes draining the DLQ — not merely registered. (We assert the DLQ
        // side only: the Floci move task removes the message from the DLQ but doesn't re-enqueue
        // it onto the source in this harness, so re-arrival on the source isn't asserted here.)
        await().atMost(Duration.ofSeconds(30)).until(() -> totalInQueue(dlqUrl) == 0);
    }

    /**
     * Seed the DLQ the real way — send to the source queue, then receive-without-deleting until
     * ApproximateReceiveCount exceeds maxReceiveCount and SQS redrives the message to the DLQ. Unlike
     * {@link #seedDlq}, the redriven message carries its original-source provenance, which is what lets
     * replayAll's destination-less move task move it back.
     */
    private void seedDlqViaRedrive(String body, String group, String dedupId) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(sourceUrl)
                .messageBody(body)
                .messageGroupId(group)
                .messageDeduplicationId(dedupId)
                .build());
        }
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .until(() -> {
                receiveWithoutDeleting(sourceUrl);
                return totalInQueue(dlqUrl) >= 1;
            });
    }

    private void receiveWithoutDeleting(String url) {
        try (SqsClient sqs = localSqsClient()) {
            // Not deleted on purpose: each receive bumps ApproximateReceiveCount toward maxReceiveCount.
            sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(url)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(1)
                .build());
        }
    }

    private void seedDlq(String body, String group, String dedupId) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(dlqUrl)
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

    private static String createFifoQueue(SqsClient sqs, String name, Map<QueueAttributeName, String> extraAttributes) {
        Map<QueueAttributeName, String> attributes = new EnumMap<>(Map.of(
            // CONTENT_BASED_DEDUPLICATION off to match the CDP-provisioned production queues, so the
            // tests exercise the explicit-dedup-id path rather than relying on SQS auto-generating one.
            QueueAttributeName.FIFO_QUEUE, "true",
            QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false"));
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
