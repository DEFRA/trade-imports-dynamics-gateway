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
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV3;

@Component
@RequiredArgsConstructor
@Slf4j
class PimsPayloadMapper {

    private final ObjectMapper objectMapper;
    private final PimsEventMapper pimsEventMapper;
    private final PimsEventMapperV2 pimsEventMapperV2;
    private final PimsEventMapperV3 pimsEventMapperV3;

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

    String mapToV3(OutboxEvent event) {
        try {
            PimsEventV3 pimsEvent = pimsEventMapperV3.map(event);
            return objectMapper.writeValueAsString(pimsEvent);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Failed to map body to PimsEventV3: " + e.getMessage(), e);
        }
    }
}
