package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;

@Component
@RequiredArgsConstructor
class PimsPayloadMapper {

    private final ObjectMapper objectMapper;
    private final PimsEventMapper pimsEventMapper;
    private final PimsEventMapperV2 pimsEventMapperV2;
    private final PimsEventMapperV3 pimsEventMapperV3;
    private final PimsEventMapperV4 pimsEventMapperV4;

    OutboxEvent parse(JsonNode body) {
        try {
            return objectMapper.treeToValue(body, OutboxEvent.class);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException("Failed to map body to OutboxEvent: " + e.getMessage(), e);
        }
    }

    String mapToV1(OutboxEvent event) {
        return serialise(pimsEventMapper.map(event));
    }

    String mapToV2(OutboxEvent event) {
        return serialise(pimsEventMapperV2.map(event));
    }

    String mapToV3(OutboxEvent event) {
        return serialise(pimsEventMapperV3.map(event));
    }

    String mapToV4(OutboxEvent event) {
        return serialise(pimsEventMapperV4.map(event));
    }

    private String serialise(Object pimsEvent) {
        try {
            return objectMapper.writeValueAsString(pimsEvent);
        } catch (JsonProcessingException e) {
            throw new SqsNonRetryableException(
                "Failed to map body to " + pimsEvent.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
