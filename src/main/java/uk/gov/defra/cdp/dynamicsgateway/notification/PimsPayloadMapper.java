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

@Component
@RequiredArgsConstructor
@Slf4j
class PimsPayloadMapper {

    private final ObjectMapper objectMapper;
    private final PimsEventMapper pimsEventMapper;

    String map(JsonNode body) {
        try {
            OutboxEvent outboxEvent = objectMapper.treeToValue(body, OutboxEvent.class);
            PimsEventV1 pimsEvent = pimsEventMapper.map(outboxEvent);
            return objectMapper.writeValueAsString(pimsEvent);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Failed to map body to PimsEventV1: " + e.getMessage(), e);
        }
    }
}
