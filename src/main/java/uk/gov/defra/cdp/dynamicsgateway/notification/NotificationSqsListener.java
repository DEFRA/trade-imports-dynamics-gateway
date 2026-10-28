package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;

/**
 * Consumes notification events from an SQS FIFO queue and forwards each external event to Azure
 * Service Bus once per PIMS schema version.
 */
@Slf4j
@Component
public class NotificationSqsListener {

    static final Set<String> EXTERNAL_EVENT_TYPES = Set.of(
        "uk.gov.defra.imports.notification.NotificationSubmitted",
        "uk.gov.defra.imports.notification.NotificationSubmissionAmended"
    );

    private static final String MESSAGES_METRIC = "notification.sqs.messages";
    private static final String OUTCOME_TAG = "outcome";

    private final QueueMessageSender queueMessageSender;
    private final ObjectMapper objectMapper;
    private final PimsPayloadMapper pimsPayloadMapper;
    private final Counter forwardedCounterV1;
    private final List<BestEffortStream> bestEffortStreams;

    private record BestEffortStream(
        PimsSchemaVersion version, String messageIdSuffix, Counter forwardedCounter, Counter failureCounter) {}

    public NotificationSqsListener(
            QueueMessageSender queueMessageSender,
            ObjectMapper objectMapper,
            PimsPayloadMapper pimsPayloadMapper,
            MeterRegistry meterRegistry) {
        this.queueMessageSender = queueMessageSender;
        this.objectMapper = objectMapper;
        this.pimsPayloadMapper = pimsPayloadMapper;
        this.forwardedCounterV1 = forwardedCounter(meterRegistry, "0.1.0");
        this.bestEffortStreams = List.of(
            bestEffortStream(meterRegistry, PimsSchemaVersion.V0_2_0, "-v2", "v2-mapping-failed"),
            bestEffortStream(meterRegistry, PimsSchemaVersion.V0_3_0, "-v3", "v3-mapping-failed"));
    }

    private static BestEffortStream bestEffortStream(
            MeterRegistry meterRegistry, PimsSchemaVersion version, String messageIdSuffix, String failureOutcome) {
        Counter failureCounter = Counter.builder(MESSAGES_METRIC)
            .tag(OUTCOME_TAG, failureOutcome)
            .description("v" + version.number()
                + " PIMS payload failed to map or publish; v0.1.0 delivery is unaffected")
            .register(meterRegistry);
        return new BestEffortStream(
            version, messageIdSuffix, forwardedCounter(meterRegistry, version.number()), failureCounter);
    }

    private static Counter forwardedCounter(MeterRegistry meterRegistry, String schemaVersion) {
        return Counter.builder(MESSAGES_METRIC)
            .tag(OUTCOME_TAG, "forwarded")
            .tag("schemaVersion", schemaVersion)
            .description("Messages successfully forwarded to ASB")
            .register(meterRegistry);
    }

    @SqsListener("${aws.sqs.notification.queue-url}")
    public void receive(
            String body,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER) String aggregateId,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER,
                required = false) String deduplicationId,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                required = false) String receiveCount) {

        log.info("Received notification event from SQS: aggregateId={}, deduplicationId={}, receiveCount={}, bodyLength={}",
            aggregateId, deduplicationId, receiveCount, body != null ? body.length() : 0);

        if (aggregateId == null || aggregateId.isBlank()) {
            throw new SqsNonRetryableException("Missing or blank MESSAGE_GROUP_ID: " + aggregateId);
        }

        if (body == null || body.isBlank()) {
            throw new SqsNonRetryableException("Empty message body");
        }

        JsonNode parsedBody;
        try {
            parsedBody = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Message body is not valid JSON", e);
        }

        String eventType = parsedBody.path("eventType").asText();
        if (!EXTERNAL_EVENT_TYPES.contains(eventType)) {
            log.info("Dropping non-external event type={} aggregateId={}", eventType, aggregateId);
            return;
        }

        String asbMessageId = resolveAsbMessageId(parsedBody, deduplicationId);
        OutboxEvent outboxEvent = pimsPayloadMapper.parse(parsedBody);

        String v1Payload = pimsPayloadMapper.mapToV1(outboxEvent);
        queueMessageSender.publish(v1Payload, aggregateId, asbMessageId);
        forwardedCounterV1.increment();

        bestEffortStreams.forEach(stream -> publishBestEffort(stream, outboxEvent, aggregateId, asbMessageId));
    }

    private void publishBestEffort(
            BestEffortStream stream, OutboxEvent outboxEvent, String aggregateId, String asbMessageId) {
        try {
            String payload = pimsPayloadMapper.mapTo(outboxEvent, stream.version());
            queueMessageSender.publish(payload, aggregateId, asbMessageId + stream.messageIdSuffix());
            stream.forwardedCounter().increment();
        } catch (Exception e) {
            log.error("Failed to map/publish v{} PIMS payload; v0.1.0 already forwarded, "
                + "message not retried for this failure: aggregateId={}, asbMessageId={}",
                stream.version().number(), aggregateId, asbMessageId, e);
            stream.failureCounter().increment();
        }
    }

    private String resolveAsbMessageId(JsonNode body, String deduplicationId) {
        return EventEnvelope.eventId(body)
            .or(() -> Optional.ofNullable(deduplicationId).filter(id -> !id.isBlank()))
            .orElseGet(() -> UUID.randomUUID().toString());
    }
}
