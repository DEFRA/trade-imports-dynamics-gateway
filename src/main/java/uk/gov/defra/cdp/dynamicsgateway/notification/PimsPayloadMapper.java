package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV1;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV2;

/**
 * Parses an SQS body once and maps it to both the v0.1.0 and v0.2.0 PIMS wire shapes, published
 * side by side while PIMS transitions from one to the other (EUDPA-370). The two map methods are
 * deliberately independent — {@link NotificationSqsListener} treats a v0.2.0 mapping failure as
 * best-effort (log and continue) so it can never affect v0.1.0 delivery, which keeps today's exact
 * failure semantics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class PimsPayloadMapper {

    private final ObjectMapper objectMapper;
    private final PimsEventMapper pimsEventMapper;
    private final PimsEventMapperV2 pimsEventMapperV2;

    OutboxEvent parse(JsonNode body) {
        try {
            return objectMapper.treeToValue(body, OutboxEvent.class);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Failed to map body to OutboxEvent: " + e.getMessage(), e);
        }
    }

    String mapToV1(OutboxEvent event) {
        try {
            PimsEventV1 pimsEvent = pimsEventMapper.map(event);
            return objectMapper.writeValueAsString(pimsEvent);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Failed to map body to PimsEventV1: " + e.getMessage(), e);
        }
    }

    String mapToV2(OutboxEvent event) {
        try {
            PimsEventV2 pimsEvent = pimsEventMapperV2.map(event);
            return objectMapper.writeValueAsString(pimsEvent);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Failed to map body to PimsEventV2: " + e.getMessage(), e);
        }
    }
}
