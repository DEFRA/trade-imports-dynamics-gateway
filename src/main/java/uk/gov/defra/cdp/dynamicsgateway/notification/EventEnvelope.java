package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class EventEnvelope {

    private EventEnvelope() {
    }

    static Optional<String> eventId(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            return eventId(objectMapper.readTree(body));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Could not parse body to read eventId, falling back", e);
            return Optional.empty();
        }
    }

    static Optional<String> eventId(JsonNode body) {
        if (body == null) {
            return Optional.empty();
        }
        JsonNode eventId = body.path("eventId");
        if (eventId.isTextual() && !eventId.asText().isBlank()) {
            return Optional.of(eventId.asText());
        }
        return Optional.empty();
    }
}
