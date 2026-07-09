package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void eventId_shouldReturnEmpty_whenFieldAbsent() {
        // Given — a valid JSON body with no eventId field
        String body = "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";

        // When
        Optional<String> result = EventEnvelope.eventId(objectMapper, body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_shouldReturnEmpty_whenValueBlank() {
        // Given — a present but blank eventId value
        String body = "{\"eventId\":\"   \",\"aggregateId\":\"" + AGGREGATE_ID + "\"}";

        // When
        Optional<String> result = EventEnvelope.eventId(objectMapper, body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_shouldReturnEmpty_whenNodeNumeric() {
        // Given — a non-textual (numeric) eventId node
        String body = "{\"eventId\":12345,\"aggregateId\":\"" + AGGREGATE_ID + "\"}";

        // When
        Optional<String> result = EventEnvelope.eventId(objectMapper, body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_shouldReturnEmpty_whenNodeObject() {
        // Given — a non-textual (object) eventId node
        String body = "{\"eventId\":{\"id\":\"" + EVENT_ID + "\"},\"aggregateId\":\"" + AGGREGATE_ID + "\"}";

        // When
        Optional<String> result = EventEnvelope.eventId(objectMapper, body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_shouldReturnEmpty_whenBodyMalformedJson() {
        // Given — an unparseable body that trips the JsonProcessingException catch/log.warn fallback
        String body = "{not valid json";

        // When
        Optional<String> result = EventEnvelope.eventId(objectMapper, body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_shouldReturnEmpty_whenBodyNull() {
        // When
        Optional<String> result = EventEnvelope.eventId(objectMapper, null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_shouldReturnEmpty_whenBodyBlank() {
        // When
        Optional<String> result = EventEnvelope.eventId(objectMapper, "   ");

        // Then
        assertThat(result).isEmpty();
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

    @Test
    void eventId_fromNode_shouldReturnEmpty_whenFieldAbsent() throws JsonProcessingException {
        // Given — a parsed JSON body with no eventId field
        JsonNode body = objectMapper.readTree(
            "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}");

        // When
        Optional<String> result = EventEnvelope.eventId(body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_fromNode_shouldReturnEmpty_whenValueBlank() throws JsonProcessingException {
        // Given — a present but blank eventId value
        JsonNode body = objectMapper.readTree(
            "{\"eventId\":\"   \",\"aggregateId\":\"" + AGGREGATE_ID + "\"}");

        // When
        Optional<String> result = EventEnvelope.eventId(body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_fromNode_shouldReturnEmpty_whenNodeNumeric() throws JsonProcessingException {
        // Given — a non-textual (numeric) eventId node
        JsonNode body = objectMapper.readTree(
            "{\"eventId\":12345,\"aggregateId\":\"" + AGGREGATE_ID + "\"}");

        // When
        Optional<String> result = EventEnvelope.eventId(body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_fromNode_shouldReturnEmpty_whenNodeObject() throws JsonProcessingException {
        // Given — a non-textual (object) eventId node
        JsonNode body = objectMapper.readTree(
            "{\"eventId\":{\"id\":\"" + EVENT_ID + "\"},\"aggregateId\":\"" + AGGREGATE_ID + "\"}");

        // When
        Optional<String> result = EventEnvelope.eventId(body);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void eventId_fromNode_shouldReturnEmpty_whenNodeNull() {
        // When
        Optional<String> result = EventEnvelope.eventId((JsonNode) null);

        // Then
        assertThat(result).isEmpty();
    }
}
