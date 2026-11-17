package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;

/**
 * Consumes notification events from an SQS FIFO queue and forwards each external event to Azure
 * Service Bus once per PIMS schema version. Each version's send counts once in
 * {@code notification.message}, tagged {@code outcome} ({@code forwarded} or {@code failed}) and
 * {@code schema.version}.
 */
@Slf4j
@Component
public class NotificationSqsListener {

    static final Set<String> EXTERNAL_EVENT_TYPES = Set.of(
        "uk.gov.defra.imports.notification.NotificationSubmitted",
        "uk.gov.defra.imports.notification.NotificationSubmissionAmended"
    );

    private final QueueMessageSender queueMessageSender;
    private final ObjectMapper objectMapper;
    private final PimsPayloadMapper pimsPayloadMapper;
    private final SchemaStream requiredStream;
    private final List<SchemaStream> bestEffortStreams;

    private record SchemaStream(
        String schemaVersion,
        Function<OutboxEvent, String> mapToPayload,
        String messageIdSuffix,
        Counter forwardedCounter,
        Counter failedCounter) {}

    public NotificationSqsListener(
            QueueMessageSender queueMessageSender,
            ObjectMapper objectMapper,
            PimsPayloadMapper pimsPayloadMapper,
            MeterRegistry meterRegistry) {
        this.queueMessageSender = queueMessageSender;
        this.objectMapper = objectMapper;
        this.pimsPayloadMapper = pimsPayloadMapper;
        this.requiredStream = schemaStream(meterRegistry, "0.1.0", pimsPayloadMapper::mapToV1, "");
        this.bestEffortStreams = List.of(
            schemaStream(meterRegistry, "0.2.0", pimsPayloadMapper::mapToV2, "-v2"),
            schemaStream(meterRegistry, "0.3.0", pimsPayloadMapper::mapToV3, "-v3"));
    }

    private static SchemaStream schemaStream(
            MeterRegistry meterRegistry,
            String schemaVersion,
            Function<OutboxEvent, String> mapToPayload,
            String messageIdSuffix) {
        return new SchemaStream(schemaVersion, mapToPayload, messageIdSuffix,
            messageCounter(meterRegistry, "forwarded", schemaVersion),
            messageCounter(meterRegistry, "failed", schemaVersion));
    }

    private static Counter messageCounter(
            MeterRegistry meterRegistry, String outcome, String schemaVersion) {
        return Counter.builder("notification.message")
            .tag("outcome", outcome)
            .tag("schema.version", schemaVersion)
            .description("PIMS payloads per schema version, forwarded to ASB or failed to map or send")
            .register(meterRegistry);
    }

    @SqsListener("${aws.sqs.notification.queue-url}")
    @Timed("listener.notification.time")
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

        publish(requiredStream, outboxEvent, aggregateId, asbMessageId);

        bestEffortStreams.forEach(stream -> publishBestEffort(stream, outboxEvent, aggregateId, asbMessageId));
    }

    private void publish(
            SchemaStream stream, OutboxEvent outboxEvent, String aggregateId, String asbMessageId) {
        try {
            String payload = stream.mapToPayload().apply(outboxEvent);
            queueMessageSender.publish(payload, aggregateId, asbMessageId + stream.messageIdSuffix());
        } catch (RuntimeException e) {
            stream.failedCounter().increment();
            throw e;
        }
        stream.forwardedCounter().increment();
    }

    private void publishBestEffort(
            SchemaStream stream, OutboxEvent outboxEvent, String aggregateId, String asbMessageId) {
        try {
            publish(stream, outboxEvent, aggregateId, asbMessageId);
        } catch (RuntimeException e) {
            log.error("Failed to map/publish v{} PIMS payload; v0.1.0 already forwarded, "
                + "message not retried for this failure: aggregateId={}, asbMessageId={}",
                stream.schemaVersion(), aggregateId, asbMessageId, e);
        }
    }

    private String resolveAsbMessageId(JsonNode body, String deduplicationId) {
        return EventEnvelope.eventId(body)
            .or(() -> Optional.ofNullable(deduplicationId).filter(id -> !id.isBlank()))
            .orElseGet(() -> UUID.randomUUID().toString());
    }
}
