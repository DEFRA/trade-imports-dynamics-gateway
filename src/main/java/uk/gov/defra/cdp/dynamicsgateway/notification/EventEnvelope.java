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
 * absent. This {@code eventId} is the dedup key carried end-to-end: it travels as the SQS
 * {@code MessageDeduplicationId} and is set downstream as the ASB {@code MessageId}. Extracting it
 * here keeps that key stable across retries, so it survives {@link DlqService} minting a fresh,
 * unique transport dedup id on replay — which is what would make ASB duplicate detection work
 * unchanged if {@code RequiresDuplicateDetection} were ever enabled (it is off today, so the id
 * currently doubles as a stable trace/correlation id).
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
            return eventId(objectMapper.readTree(body));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Could not parse body to read eventId, falling back", e);
            return Optional.empty();
        }
    }

    /**
     * Extract the top-level {@code eventId} from an already-parsed JSON body, if present and
     * non-blank. Use this overload when the caller has already parsed the body (e.g. to validate it
     * is JSON) to avoid re-parsing the same message twice.
     *
     * @param body the parsed message body; may be null
     * @return the {@code eventId}, or empty when the node is null/absent/blank/non-textual
     */
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
