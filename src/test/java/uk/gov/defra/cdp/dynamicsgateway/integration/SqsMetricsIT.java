package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.SQS_GET_QUEUE_ATTRIBUTES;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.SQS_RECEIVE_MESSAGE;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.durationCount;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.failureTotal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.floci.testcontainers.FlociContainer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import uk.gov.defra.cdp.dynamicsgateway.configuration.NotificationSqsConfig;
import uk.gov.defra.cdp.dynamicsgateway.metrics.Dependency;
import uk.gov.defra.cdp.dynamicsgateway.metrics.ExternalCallRecorder;
import uk.gov.defra.cdp.dynamicsgateway.metrics.LastCallOutcomes;
import uk.gov.defra.cdp.dynamicsgateway.metrics.QueueDepthMetrics;
import uk.gov.defra.cdp.dynamicsgateway.metrics.SqsCallRecorder;
import uk.gov.defra.cdp.dynamicsgateway.notification.DlqService;
import uk.gov.defra.cdp.dynamicsgateway.notification.NotificationQueueService;

class SqsMetricsIT {

    private static final String REGION = "us-east-1";
    private static final String UNUSED_DLQ_ARN = "arn:aws:sqs:us-east-1:000000000000:unused";
    private static final String GROUP = "Imports.Notification.GBN-AG.GBN-AG-26-001";

    private static final FlociContainer FLOCI = new FlociContainer(
        DockerImageName.parse("floci/floci:latest"))
        .withRegion(REGION);

    private static final MeterRegistry METER_REGISTRY = new SimpleMeterRegistry();
    private static final LastCallOutcomes LAST_CALL_OUTCOMES = new LastCallOutcomes();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static SqsAsyncClient asyncSqsClient;
    private static String listedDlqUrl;
    private static String depthSourceUrl;
    private static String depthDlqUrl;

    static {
        FLOCI.start();
    }

    @BeforeAll
    static void createQueues() {
        asyncSqsClient = buildAsyncSqsClient();
        try (SqsClient sqs = localSqsClient()) {
            listedDlqUrl = createFifoQueue(sqs, "listed-dlq.fifo");
            depthSourceUrl = createFifoQueue(sqs, "depth-source.fifo");
            depthDlqUrl = createFifoQueue(sqs, "depth-dlq.fifo");
        }
    }

    @AfterAll
    static void closeAsyncClient() {
        asyncSqsClient.close();
    }

    @Test
    void list_recordsEachSqsActionAsAnSqsCall_andSqsAsAvailable() {
        long attributeReadsBefore = durationCount(METER_REGISTRY, SQS_GET_QUEUE_ATTRIBUTES);
        long receivesBefore = durationCount(METER_REGISTRY, SQS_RECEIVE_MESSAGE);

        dlqServiceFor(listedDlqUrl).list(1);

        assertThat(durationCount(METER_REGISTRY, SQS_GET_QUEUE_ATTRIBUTES))
            .isEqualTo(attributeReadsBefore + 1);
        assertThat(durationCount(METER_REGISTRY, SQS_RECEIVE_MESSAGE)).isGreaterThan(receivesBefore);
        assertThat(LAST_CALL_OUTCOMES.lastCallSucceeded(Dependency.SQS)).contains(true);
    }

    @Test
    void aCallToAMissingQueueIsRecordedAsAFailedSqsCall_andSqsAsUnavailable() {
        double failuresBefore = failureTotal(METER_REGISTRY, SQS_GET_QUEUE_ATTRIBUTES);
        DlqService missingQueue = dlqServiceFor(missingQueueUrl());

        assertThatExceptionOfType(CompletionException.class).isThrownBy(missingQueue::dlqDepth);

        assertThat(failureTotal(METER_REGISTRY, SQS_GET_QUEUE_ATTRIBUTES))
            .isEqualTo(failuresBefore + 1);
        assertThat(LAST_CALL_OUTCOMES.lastCallSucceeded(Dependency.SQS)).contains(false);
    }

    @Test
    void queueDepthGauge_readsTheMessagesWaitingOnEachQueue() {
        sendTo(depthDlqUrl, "{\"key\":\"a\"}", "dedup-a");
        sendTo(depthDlqUrl, "{\"key\":\"b\"}", "dedup-b");
        sendTo(depthSourceUrl, "{\"key\":\"c\"}", "dedup-c");
        MeterRegistry registry = new SimpleMeterRegistry();
        NotificationSqsConfig config =
            new NotificationSqsConfig(depthSourceUrl, depthDlqUrl, UNUSED_DLQ_ARN, 20, 10);
        new QueueDepthMetrics(
            registry,
            new NotificationQueueService(asyncSqsClient, config),
            dlqServiceFor(depthDlqUrl));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(queueDepth(registry, "notification-dlq")).isEqualTo(2.0);
            assertThat(queueDepth(registry, "notification")).isEqualTo(1.0);
        });
    }

    @Test
    void queueDepthGauge_isNaN_whenTheQueueCannotBeRead() {
        MeterRegistry registry = new SimpleMeterRegistry();
        NotificationSqsConfig config =
            new NotificationSqsConfig(missingQueueUrl(), missingQueueUrl(), UNUSED_DLQ_ARN, 20, 10);
        new QueueDepthMetrics(
            registry,
            new NotificationQueueService(asyncSqsClient, config),
            dlqServiceFor(missingQueueUrl()));

        assertThat(queueDepth(registry, "notification-dlq")).isNaN();
        assertThat(queueDepth(registry, "notification")).isNaN();
    }

    private static double queueDepth(MeterRegistry registry, String queue) {
        return registry.get("sqs.queue.depth").tag("queue", queue).gauge().value();
    }

    private static String missingQueueUrl() {
        return listedDlqUrl.replace("listed-dlq.fifo", "missing-queue.fifo");
    }

    private static DlqService dlqServiceFor(String dlqUrl) {
        return new DlqService(
            asyncSqsClient,
            new NotificationSqsConfig("http://localhost/unused-source-queue", dlqUrl, UNUSED_DLQ_ARN, 20, 10),
            OBJECT_MAPPER);
    }

    private static void sendTo(String queueUrl, String body, String dedupId) {
        try (SqsClient sqs = localSqsClient()) {
            sqs.sendMessage(request -> request
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageGroupId(GROUP)
                .messageDeduplicationId(dedupId));
        }
    }

    private static String createFifoQueue(SqsClient sqs, String name) {
        return sqs.createQueue(request -> request
                .queueName(name)
                .attributes(Map.of(
                    QueueAttributeName.FIFO_QUEUE, "true",
                    QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "false")))
            .queueUrl();
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
        SqsCallRecorder sqsCallRecorder =
            new SqsCallRecorder(new ExternalCallRecorder(METER_REGISTRY, LAST_CALL_OUTCOMES));
        return SqsAsyncClient.builder()
            .endpointOverride(URI.create(FLOCI.getEndpoint()))
            .region(Region.of(REGION))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(FLOCI.getAccessKey(), FLOCI.getSecretKey())))
            .overrideConfiguration(c -> c.addExecutionInterceptor(sqsCallRecorder))
            .build();
    }
}
