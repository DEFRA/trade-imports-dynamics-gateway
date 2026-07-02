package uk.gov.defra.cdp.dynamicsgateway.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the stable {@code eventId} from an enveloped notification body.
 *
 * <p>The backend outbox publishes the full event envelope (carrying {@code eventId}) in the message
 * body (EUDPA-261). Callers fall back to the SQS {@code MessageDeduplicationId} when the field is
 * absent. Using {@code eventId} as the ASB {@code messageId} keeps the dedup key stable end-to-end
 * and survives {@link DlqService} minting a fresh, unique transport dedup id on replay — see
 * {@code docs/notification-pipeline-dedup.md}.
 */
@Slf4j
final class EventEnvelope {

    private EventEnvelope() {
    }

    /**
     * Extract the top-level {@code eventId} from a JSON message body, if present and non-blank.
     *
     * @param objectMapper shared mapper
     * @param body         the raw message body; may be null, blank or non-JSON
     * @return the {@code eventId}, or empty when absent/blank/unparseable
     */
    static Optional<String> eventId(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode eventId = objectMapper.readTree(body).path("eventId");
            if (eventId.isTextual() && !eventId.asText().isBlank()) {
                return Optional.of(eventId.asText());
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Could not parse body to read eventId, falling back", e);
        }
        return Optional.empty();
    }
}
