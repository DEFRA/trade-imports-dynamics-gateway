package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;
import uk.gov.defra.cdp.dynamicsgateway.notification.DlqListResponse;
import uk.gov.defra.cdp.dynamicsgateway.notification.DlqService;

/**
 * Exercises {@link DlqService} against a real SQS (LocalStack) so the net-new DLQ plumbing — FIFO
 * receive semantics, receipt-handle lifecycle, system attributes, {@code GetQueueAttributes} counting
 * and the early visibility-release — runs against the actual API rather than mocks. Standalone (no
 * Spring context, no ASB emulator): replay's downstream consume → ASB publish is the existing
 * source-queue path already covered by {@link NotificationSqsListenerIT}, so this asserts replay lands
 * the message back on the source queue.
 */
@Slf4j
class DlqServiceIT {

    private static final String SOURCE_QUEUE = "trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String DLQ_QUEUE = "trade_imports_animals_eu_notifications_gateway-deadletter.fifo";
    private static final String GROUP_A = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final String GROUP_B = "Imports.Notification.GBN-AG.GBN-AG-26-002";

    // Newer than the listener IT's 3.0.2: that build predates LocalStack's SQS JSON protocol support
    // and silently ignores the MessageSystemAttributeNames request param, so it returns no message
    // group / dedup id — unlike real SQS. DlqService reads those attributes, so it needs a LocalStack
    // that honours the param the way production AWS does.
    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.8.1"))
        .withServices(LocalStackContainer.Service.SQS);

    private static String sourceQueueUrl;
    private static String dlqUrl;
    private static SqsAsyncClient asyncSqsClient;

    static {
        LOCAL_STACK.start();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DlqService dlqService;

    @BeforeAll
    static void createQueues() {
        asyncSqsClient = buildAsyncSqsClient();
        try (SqsClient sqs = localSqsClient()) {
            sourceQueueUrl = createFifoQueue(sqs, SOURCE_QUEUE);
            dlqUrl = createFifoQueue(sqs, DLQ_QUEUE);
        }
    }

    @AfterAll
    static void closeAsyncClient() {
        asyncSqsClient.close();
    }

    @BeforeEach
    void setUp() {
        purge(sourceQueueUrl);
        purge(dlqUrl);
        NotificationSqsConfig config = new NotificationSqsConfig(
            sourceQueueUrl, dlqUrl, 30, 20, 10,
            new NotificationSqsConfig.Retry(4, 1000, 2.0, 10000));
        dlqService = new DlqService(asyncSqsClient, config, objectMapper);
    }

    @Test
    void list_returnsSeededMessagesWithAttributesAndCount() {
        seedDlq("{\"key\":\"a\"}", GROUP_A, "dedup-a");
        seedDlq("{\"key\":\"b\"}", GROUP_B, "dedup-b");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 2);

        DlqListResponse response = dlqService.list(10);

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
        // 13 > the 10-per-receive SQS cap, so list must page; distinct groups avoid FIFO blocking.
        for (int i = 0; i < 13; i++) {
            seedDlq("{\"key\":\"" + i + "\"}", GROUP_A + "-" + i, "dedup-" + i);
        }
        await().atMost(Duration.ofSeconds(15)).until(() -> totalInQueue(dlqUrl) == 13);

        assertThat(dlqService.list(50).messages()).hasSize(13);

        // Non-destructive: every browsed message is released back, so the depth is unchanged.
        await().atMost(Duration.ofSeconds(15)).until(() -> totalInQueue(dlqUrl) == 13);
    }

    @Test
    void list_honoursLimitSmallerThanQueueDepth() {
        for (int i = 0; i < 5; i++) {
            seedDlq("{\"key\":\"" + i + "\"}", GROUP_A + "-" + i, "dedup-" + i);
        }
        await().atMost(Duration.ofSeconds(15)).until(() -> totalInQueue(dlqUrl) == 5);

        assertThat(dlqService.list(3).messages()).hasSize(3);
    }

    @Test
    void replay_reSendsToSourceQueueAndRemovesFromDlq() {
        seedDlq("{\"key\":\"a\"}", GROUP_A, "dedup-a");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 1);

        dlqService.replay(List.of("dedup-a"));

        Optional<Message> onSource = receiveFromSource();
        assertThat(onSource).isPresent();
        assertThat(onSource.get().body()).isEqualTo("{\"key\":\"a\"}");
        assertThat(onSource.get().attributes())
            .containsEntry(MessageSystemAttributeName.MESSAGE_GROUP_ID, GROUP_A)
            .containsEntry(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID, "dedup-a");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 0);
    }

    @Test
    void replay_usesEventIdAsSourceDedupId_whenBodyIsEnveloped() {
        seedDlq("{\"eventId\":\"evt-42\",\"key\":\"a\"}", GROUP_A, "dedup-a");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 1);

        dlqService.replay(List.of("evt-42"));

        Optional<Message> onSource = receiveFromSource();
        assertThat(onSource).isPresent();
        assertThat(onSource.get().attributes())
            .containsEntry(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID, "evt-42");
    }

    @Test
    void replay_reportsNotFoundAndLeavesMessage_whenIdNotOnQueue() {
        seedDlq("{\"key\":\"a\"}", GROUP_A, "dedup-a");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 1);

        dlqService.replay(List.of("missing"));

        // Nothing replayed: the message stays on the DLQ and nothing reaches the source queue.
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 1);
        assertThat(receiveFromSource()).isEmpty();
    }

    @Test
    void delete_removesSelectedAndLeavesOthersImmediatelyVisible() {
        seedDlq("{\"key\":\"a\"}", GROUP_A, "dedup-a");
        seedDlq("{\"key\":\"b\"}", GROUP_B, "dedup-b");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 2);

        dlqService.delete(List.of("dedup-a"));

        // Target gone; the untargeted message was released back, so it lists straight away (no 30s wait).
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            var messages = dlqService.list(10).messages();
            return messages.size() == 1 && messages.getFirst().id().equals("dedup-b");
        });
    }

    @Test
    void delete_removesEverySelectedId_inOneCall() {
        seedDlq("{\"key\":\"a\"}", GROUP_A, "dedup-a");
        seedDlq("{\"key\":\"b\"}", GROUP_B, "dedup-b");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 2);

        dlqService.delete(List.of("dedup-a", "dedup-b"));

        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 0);
    }

    @Test
    void delete_reportsNotFound_whenIdNotOnQueue() {
        seedDlq("{\"key\":\"a\"}", GROUP_A, "dedup-a");
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 1);

        dlqService.delete(List.of("missing"));

        // Nothing matched, so the message stays on the DLQ.
        await().atMost(Duration.ofSeconds(10)).until(() -> totalInQueue(dlqUrl) == 1);
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

    private Optional<Message> receiveFromSource() {
        try (SqsClient sqs = localSqsClient()) {
            return sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(sourceQueueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(2)
                    .messageSystemAttributeNames(
                        MessageSystemAttributeName.MESSAGE_GROUP_ID,
                        MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID)
                    .build())
                .messages()
                .stream()
                .findFirst();
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
        sqs.createQueue(CreateQueueRequest.builder()
            .queueName(name)
            // CONTENT_BASED_DEDUPLICATION off to match the CDP-provisioned production queues, so the
            // tests exercise the explicit-dedup-id path rather than relying on SQS auto-generating one.
            .attributes(Map.of(
                QueueAttributeName.FIFO_QUEUE, "true",
                QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false"))
            .build());
        return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
    }

    private static SqsClient localSqsClient() {
        return SqsClient.builder()
            .endpointOverride(LOCAL_STACK.getEndpointOverride(LocalStackContainer.Service.SQS))
            .region(Region.of("us-east-1"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
            .build();
    }

    private static SqsAsyncClient buildAsyncSqsClient() {
        return SqsAsyncClient.builder()
            .endpointOverride(LOCAL_STACK.getEndpointOverride(LocalStackContainer.Service.SQS))
            .region(Region.of("us-east-1"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
            .build();
    }
}
