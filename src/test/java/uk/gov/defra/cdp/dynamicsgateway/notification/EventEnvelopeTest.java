package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EventEnvelopeTest {

    private static final String EVENT_ID = "evt-99999999-8888-7777-6666-555555555555";
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void eventId_shouldReturnValue_whenPresentAndTextual() {
        // Given — an enveloped body carrying a textual eventId
        String body = "{\"eventId\":\"" + EVENT_ID + "\",\"aggregateId\":\"" + AGGREGATE_ID + "\"}";

        // When
        Optional<String> result = EventEnvelope.eventId(objectMapper, body);

        // Then
        assertThat(result).contains(EVENT_ID);
    }

    // Covers: absent field, blank value, non-textual (numeric/object) node,
    // malformed JSON (JsonProcessingException catch/log.warn fallback), and
    // null/blank body — every path where the String overload yields no eventId.
    @ParameterizedTest
    @NullSource
    @ValueSource(
        strings = {
            "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}",
            "{\"eventId\":\"   \",\"aggregateId\":\"" + AGGREGATE_ID + "\"}",
            "{\"eventId\":12345,\"aggregateId\":\"" + AGGREGATE_ID + "\"}",
            "{\"eventId\":{\"id\":\"" + EVENT_ID + "\"},\"aggregateId\":\"" + AGGREGATE_ID + "\"}",
            "{not valid json",
            "   "
        })
    void eventId_shouldReturnEmpty_whenBodyHasNoUsableEventId(String body) {
        // When / Then
        assertThat(EventEnvelope.eventId(objectMapper, body)).isEmpty();
    }

    @Test
    void eventId_fromNode_shouldReturnValue_whenPresentAndTextual() throws JsonProcessingException {
        // Given — an already-parsed enveloped body carrying a textual eventId
        JsonNode body = objectMapper.readTree(
            "{\"eventId\":\"" + EVENT_ID + "\",\"aggregateId\":\"" + AGGREGATE_ID + "\"}");

        // When
        Optional<String> result = EventEnvelope.eventId(body);

        // Then
        assertThat(result).contains(EVENT_ID);
    }

    // Covers: absent field, blank value, non-textual (numeric/object) node, and
    // a null node — every path where the JsonNode overload yields no eventId.
    @ParameterizedTest
    @NullSource
    @ValueSource(
        strings = {
            "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}",
            "{\"eventId\":\"   \",\"aggregateId\":\"" + AGGREGATE_ID + "\"}",
            "{\"eventId\":12345,\"aggregateId\":\"" + AGGREGATE_ID + "\"}",
            "{\"eventId\":{\"id\":\"" + EVENT_ID + "\"},\"aggregateId\":\"" + AGGREGATE_ID + "\"}"
        })
    void eventId_fromNode_shouldReturnEmpty_whenNodeHasNoUsableEventId(String json)
        throws JsonProcessingException {
        // Given — a null node, or a parsed node with no usable eventId
        JsonNode body = json == null ? null : objectMapper.readTree(json);

        // When / Then
        assertThat(EventEnvelope.eventId(body)).isEmpty();
    }
}
