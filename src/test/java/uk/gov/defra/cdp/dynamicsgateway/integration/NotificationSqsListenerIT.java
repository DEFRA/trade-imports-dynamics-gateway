package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.defra.cdp.dynamicsgateway.metrics.ExternalCall.SERVICE_BUS_SEND_MESSAGE;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.SQS_RECEIVE_MESSAGE;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.availability;
import static uk.gov.defra.cdp.dynamicsgateway.testsupport.ExternalCallMeters.durationCount;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.azure.core.amqp.exception.AmqpErrorCondition;
import com.azure.core.amqp.exception.AmqpException;
import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import io.floci.testcontainers.FlociContainer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import uk.gov.defra.cdp.dynamicsgateway.metrics.Dependency;

@Slf4j
class NotificationSqsListenerIT extends IntegrationBase {

    private static final String QUEUE_NAME_SQS = "trade_imports_animals_eu_notifications_gateway.fifo";
    private static final String DLQ_NAME_SQS = "trade_imports_animals_eu_notifications_gateway-deadletter.fifo";
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final Duration ASB_POLL_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_RECEIVE_COUNT = 3;
    private static final int VISIBILITY_TIMEOUT_SECONDS = 5;

    // Floci scopes SQS queues per region, so the test client and the Floci container must
    // sign for the same region as the app under test — eu-west-2, the app's default.
    private static final String AWS_REGION = "eu-west-2";

    private static final FlociContainer FLOCI = new FlociContainer(
        DockerImageName.parse("floci/floci:latest"))
        .withRegion(AWS_REGION);

    private static String queueUrl;
    private static String dlqUrl;

    static {
        FLOCI.start();
    }

    @BeforeAll
    static void createQueues() {
        try (SqsClient sqs = localSqsClient()) {
            dlqUrl = createFifoQueue(sqs, DLQ_NAME_SQS, Map.of());
            String dlqArn = queueArn(sqs, dlqUrl);
            queueUrl = createFifoQueue(sqs, QUEUE_NAME_SQS, Map.of(
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"" + MAX_RECEIVE_COUNT + "\"}",
                QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(VISIBILITY_TIMEOUT_SECONDS)));
        }
    }

    @MockitoSpyBean
    private ServiceBusSenderClient senderClient;

    @Autowired
    private MeterRegistry meterRegistry;

    @DynamicPropertySource
    static void setFlociProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.sqs.notification.queue-url", () -> queueUrl);
        registry.add("aws.sqs.notification.wait-time-seconds", () -> "1");
        registry.add("app.aws.endpoint-override", FLOCI::getEndpoint);
        registry.add("app.aws.access-key-id", FLOCI::getAccessKey);
        registry.add("app.aws.secret-access-key", FLOCI::getSecretKey);
    }

    @BeforeEach
    void purgeQueuesAndResetSpy() {
        Mockito.reset(senderClient);
        purgeQueue(queueUrl);
        purgeQueue(dlqUrl);
        drainAsb();
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
            // The ASB body is the re-serialised PimsEventV1, not the raw SQS body.
            String asbBody = received.get().getBody().toString();
            JsonNode pimsNode = new ObjectMapper().readTree(asbBody);
            assertThat(pimsNode.path("aggregateId").asText()).isEqualTo(AGGREGATE_ID);
            assertThat(pimsNode.path("eventType").asText())
                .isEqualTo("uk.gov.defra.imports.notification.NotificationSubmitted");
            assertThat(received.get().getRawAmqpMessage().getProperties().getContentType()).isEqualTo("application/json");
            // The inbound SQS MessageDeduplicationId is carried through as the ASB messageId.
            assertThat(received.get().getMessageId()).isEqualTo(deduplicationId);
            assertThat(received.get().getSessionId()).isEqualTo(AGGREGATE_ID);
        });
    }

    @Test
    void sqsToAsb_shouldEmitEverySchemaVersionInOrderOnOneSession_forOneNotification() {
        // Given
        String deduplicationId = UUID.randomUUID().toString();
        sendToSqs(notificationJsonWithoutEventId(), AGGREGATE_ID, deduplicationId);

        List<ServiceBusReceivedMessage> versions = awaitFourOrderedAsbMessages(deduplicationId);
        assertThat(versions).extracting(ServiceBusReceivedMessage::getSessionId).containsOnly(AGGREGATE_ID);
        assertEverySchemaVersionPayload(versions);
    }

    private List<ServiceBusReceivedMessage> awaitFourOrderedAsbMessages(String deduplicationId) {
        String v1Id = deduplicationId;
        String v2Id = deduplicationId + "-v2";
        String v3Id = deduplicationId + "-v3";
        String v4Id = deduplicationId + "-v4";
        List<ServiceBusReceivedMessage> mine = new ArrayList<>();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            collectMessagesFor(mine, deduplicationId);
            assertThat(messageIds(mine))
                .as("one inbound notification must produce exactly four ASB messages")
                .containsExactlyInAnyOrder(v1Id, v2Id, v3Id, v4Id);
        });

        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            collectMessagesFor(mine, deduplicationId);
            assertThat(messageIds(mine))
                .as("no fifth message may follow the v0.1.0–v0.4.0 messages, which arrive in order")
                .containsExactly(v1Id, v2Id, v3Id, v4Id);
        });

        return List.of(
            messageWithId(mine, v1Id),
            messageWithId(mine, v2Id),
            messageWithId(mine, v3Id),
            messageWithId(mine, v4Id));
    }

    private void assertEverySchemaVersionPayload(List<ServiceBusReceivedMessage> versions) {
        ServiceBusReceivedMessage v1 = versions.get(0);
        ServiceBusReceivedMessage v2 = versions.get(1);
        ServiceBusReceivedMessage v3 = versions.get(2);
        ServiceBusReceivedMessage v4 = versions.get(3);

        JsonNode v3Body = readBody(v3);
        assertThat(v3Body.path("metadata").path("schemaVersion").asText()).isEqualTo("0.3.0");
        assertThat(v3Body.path("metadata").path("schemaUri").asText()).endsWith("gbn-ag-pims-v0.3.0.schema.json");
        assertThat(v3Body.path("data").path("specifiedConsignment").path("originCountry")
            .path("regionOfOriginCode").asText()).isEqualTo("ES-12345");

        JsonNode v1Metadata = readBody(v1).path("metadata");
        assertThat(v1Metadata.path("schemaVersion").asText()).isEqualTo("0.1.0");
        assertThat(v1Metadata.path("schemaUri").asText()).endsWith("gbn-ag-pims-v0.1.0.schema.json");

        JsonNode v2Body = readBody(v2);
        JsonNode v2Metadata = v2Body.path("metadata");
        assertThat(v2Metadata.path("schemaVersion").asText()).isEqualTo("0.2.0");
        assertThat(v2Metadata.path("schemaUri").asText()).endsWith("gbn-ag-pims-v0.2.0.schema.json");
        assertThat(v2Metadata.has("schemaUrl"))
            .as("metadata.schemaUrl must not appear on the wire — the envelope forbids it")
            .isFalse();

        JsonNode consignment = v2Body.path("data").path("specifiedConsignment");
        JsonNode subdivision = consignment.path("originCountry").path("subordinateTradeCountrySubDivision");
        assertThat(subdivision.isObject())
            .as("subordinateTradeCountrySubDivision is a single object in v0.2.0, not an array")
            .isTrue();
        assertThat(subdivision.isArray()).isFalse();
        assertThat(subdivision.path("identifier").asText()).isEqualTo("ES-CN");
        assertThat(subdivision.path("urlId").asText())
            .isEqualTo("https://refdata.tbc.defra.gov.uk/country_subdivisions");
        assertThat(subdivision.path("functionTypeCode").path("content").asText()).isEqualTo("106");
        assertThat(consignment.path("originCountry").has("regionOfOriginCode"))
            .as("regionOfOriginCode is not part of v0.2.0")
            .isFalse();

        JsonNode lineItems = consignment.path("includedConsignmentItem").get(0).path("includedTradeLineItem");
        assertThat(lineItems).hasSize(2);
        assertThat(lineItems.get(0).path("applicableClassification").isArray()).isTrue();
        assertThat(lineItems.get(0).path("applicableClassification")).hasSize(1);
        assertThat(lineItems.get(0).path("applicableClassification").get(0).path("systemId").asText())
            .isEqualTo("CN");
        assertThat(lineItems.get(1).has("applicableClassification"))
            .as("applicableClassification must be omitted rather than emitted as an empty array")
            .isFalse();

        JsonNode v4Metadata = readBody(v4).path("metadata");
        assertThat(v4Metadata.path("schemaVersion").asText()).isEqualTo("0.4.0");
        assertThat(v4Metadata.path("schemaUri").asText()).endsWith("gbn-ag-pims-v0.4.0.schema.json");
    }

    @Test
    void sqsToAsb_shouldCountForwardedMessages_whenValidEvent() {
        // Given
        double baseV1 = forwardedCount("0.1.0");
        double baseV2 = forwardedCount("0.2.0");
        double baseV3 = forwardedCount("0.3.0");
        double baseV4 = forwardedCount("0.4.0");
        String deduplicationId = UUID.randomUUID().toString();
        sendToSqs(notificationJsonWithoutEventId(), AGGREGATE_ID, deduplicationId);

        // When / Then
        List<ServiceBusReceivedMessage> mine = new ArrayList<>();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            collectMessagesFor(mine, deduplicationId);
            assertThat(forwardedCount("0.1.0")).isEqualTo(baseV1 + 1);
            assertThat(forwardedCount("0.2.0")).isEqualTo(baseV2 + 1);
            assertThat(forwardedCount("0.3.0")).isEqualTo(baseV3 + 1);
            assertThat(forwardedCount("0.4.0")).isEqualTo(baseV4 + 1);
            assertThat(messageIds(mine))
                .containsExactlyInAnyOrder(
                    deduplicationId, deduplicationId + "-v2", deduplicationId + "-v3", deduplicationId + "-v4");
        });
    }

    @Test
    void sqsToAsb_recordsTheListenerTime_fourServiceBusSends_theSqsPolls_andBothDependenciesAvailable() {
        long baseListenerCalls = listenerCallCount();
        long baseSends = durationCount(meterRegistry, SERVICE_BUS_SEND_MESSAGE);
        long baseReceives = durationCount(meterRegistry, SQS_RECEIVE_MESSAGE);
        String deduplicationId = UUID.randomUUID().toString();
        sendToSqs(notificationJsonWithoutEventId(), AGGREGATE_ID, deduplicationId);

        List<ServiceBusReceivedMessage> mine = new ArrayList<>();
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            collectMessagesFor(mine, deduplicationId);
            assertThat(listenerCallCount()).isEqualTo(baseListenerCalls + 1);
            assertThat(durationCount(meterRegistry, SERVICE_BUS_SEND_MESSAGE))
                .isEqualTo(baseSends + 4);
            assertThat(durationCount(meterRegistry, SQS_RECEIVE_MESSAGE)).isGreaterThan(baseReceives);
            assertThat(availability(meterRegistry, Dependency.AZURE_SERVICE_BUS)).isEqualTo(1.0);
            assertThat(availability(meterRegistry, Dependency.SQS)).isEqualTo(1.0);
        });
    }

    @Test
    void sqsToAsb_shouldLeaveMessageInSqs_whenAsbFailureIsTransient() {
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = notificationJson(AGGREGATE_ID);
        sendToSqs(eventBody, AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            verify(senderClient, times(1)).sendMessage(any(ServiceBusMessage.class)));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(totalMessagesInQueue())
                .as("after a transient failure the message must remain in SQS for redelivery, not be deleted")
                .isEqualTo(1));
    }

    @Test
    void sqsToAsb_shouldForwardMessage_whenAsbFailsTransientlyThenRecovers() {
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).doCallRealMethod().when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = notificationJson(AGGREGATE_ID);
        sendToSqs(eventBody, AGGREGATE_ID);

        // When / Then — the redelivered attempt forwards the message to ASB.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Optional<ServiceBusReceivedMessage> received = tryReceiveFromAsb();
            assertThat(received).isPresent();
            // The ASB body is the re-serialised PimsEventV1.
            JsonNode pimsNode = new ObjectMapper().readTree(received.get().getBody().toString());
            assertThat(pimsNode.path("aggregateId").asText()).isEqualTo(AGGREGATE_ID);
        });
        // It took more than one attempt — the transient failure was retried, not discarded on first failure.
        verify(senderClient, atLeast(2)).sendMessage(any(ServiceBusMessage.class));
        // And the message is gone from the source queue: acked once the retry forwarded it successfully.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(totalMessagesInQueue())
                .as("after a successful retry the message must be removed from the source queue")
                .isZero());
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
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);
        doThrow(transientEx).when(senderClient).sendMessage(any(ServiceBusMessage.class));

        String eventBody = notificationJson(AGGREGATE_ID);
        sendToSqs(eventBody, AGGREGATE_ID);

        await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
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

    @Test
    void sqsToAsb_shouldDropMessage_whenEventTypeIsNotificationEdited() {
        String editedBody = "{\"aggregateId\":\"" + AGGREGATE_ID
            + "\",\"eventType\":\"uk.gov.defra.trade.imports.animals.NotificationEdited\"}";
        sendToSqs(editedBody, AGGREGATE_ID);

        // Allow time for the listener to consume the message, then assert nothing reached ASB
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Optional<ServiceBusReceivedMessage> received = tryReceiveFromAsb();
            assertThat(received).isEmpty();
        });
    }

    private long listenerCallCount() {
        return meterRegistry.find("listener.notification.time").timers().stream()
            .mapToLong(Timer::count)
            .sum();
    }

    private double forwardedCount(String schemaVersion) {
        return meterRegistry.get("notification.message")
            .tag("outcome", "forwarded")
            .tag("schema.version", schemaVersion)
            .counter()
            .count();
    }

    private static String notificationJson(String aggregateId) {
        return "{\"aggregateId\":\"" + aggregateId
            + "\",\"eventType\":\"uk.gov.defra.imports.notification.NotificationSubmitted\"}";
    }

    private static String notificationJsonWithoutEventId() {
        return """
            {
              "aggregateId": "%s",
              "aggregateType": "Notification",
              "subType": "GBN-AG",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "metadata": {
                "correlationId": "cid-dual-emit",
                "schemaVersion": "1",
                "schemaUrl": "https://example.invalid/generic-side.schema.json"
              },
              "data": {
                "$model": "defra/certificate-internal/1",
                "$type": "gbn-ag",
                "specifiedConsignment": {
                  "originCountry": {
                    "code": { "value": "ES" },
                    "subordinateTradeCountrySubDivision": [
                      {
                        "identifier": "ES-CN",
                        "urlId": "https://refdata.tbc.defra.gov.uk/country_subdivisions",
                        "functionTypeCode": { "content": "106" }
                      }
                    ],
                    "regionOfOriginCode": "ES-12345"
                  },
                  "includedConsignmentItem": [
                    {
                      "includedTradeLineItem": [
                        {
                          "commonName": "Cow",
                          "applicableClassification": [
                            { "systemId": "CN", "classCode": { "value": "0102" } }
                          ]
                        },
                        { "commonName": "Dog" }
                      ]
                    }
                  ]
                }
              }
            }
            """.formatted(AGGREGATE_ID);
    }

    private static JsonNode readBody(ServiceBusReceivedMessage message) {
        try {
            return new ObjectMapper().readTree(message.getBody().toString());
        } catch (Exception e) {
            throw new IllegalStateException("ASB message body was not valid JSON", e);
        }
    }

    private void collectMessagesFor(List<ServiceBusReceivedMessage> into, String deduplicationId) {
        consumeFromNextAsbSession(10, ASB_POLL_TIMEOUT).stream()
            .filter(m -> m.getMessageId() != null && m.getMessageId().startsWith(deduplicationId))
            .forEach(into::add);
    }

    private static List<String> messageIds(List<ServiceBusReceivedMessage> messages) {
        return messages.stream().map(ServiceBusReceivedMessage::getMessageId).toList();
    }

    private static ServiceBusReceivedMessage messageWithId(
            List<ServiceBusReceivedMessage> messages, String messageId) {
        return messages.stream()
            .filter(m -> messageId.equals(m.getMessageId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No ASB message with messageId=" + messageId + "; got "
                    + messages.stream().map(ServiceBusReceivedMessage::getMessageId).toList()));
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
        return consumeFromNextAsbSession(1, ASB_POLL_TIMEOUT).stream().findFirst();
    }

    private static SqsClient localSqsClient() {
        return SqsClient.builder()
            .endpointOverride(URI.create(FLOCI.getEndpoint()))
            .region(Region.of(AWS_REGION))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(FLOCI.getAccessKey(), FLOCI.getSecretKey())))
            .build();
    }
}
