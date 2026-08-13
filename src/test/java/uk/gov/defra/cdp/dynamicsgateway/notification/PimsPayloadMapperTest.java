package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;

class PimsPayloadMapperTest {

    private PimsPayloadMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PimsEventMapper pimsEventMapper = new PimsEventMapper(
            new PimsGbnAgDataMapper(
                new PimsConsignmentMapper(
                    new PimsTransportMapper(),
                    new PimsLineItemMapper()
                )
            )
        );
        mapper = new PimsPayloadMapper(objectMapper, pimsEventMapper);
    }

    @Test
    void map_shouldRoundTripMinimalBody_toValidPimsPayload() throws Exception {
        // Given — minimal body with just the envelope fields (data/actor/metadata all null)
        String body = """
            {
              "eventId": "evt-1",
              "aggregateType": "Notification",
              "subType": "GBN-AG",
              "aggregateId": "Imports.Notification.GBN-AG.GBN-AG-26-001",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "timestamp": "2026-08-12T10:00:00Z"
            }
            """;
        JsonNode node = objectMapper.readTree(body);

        // When
        String result = mapper.map(node);

        // Then — re-serialised PimsEventV1 contains the envelope fields; nulls omitted by @JsonInclude
        JsonNode pimsNode = objectMapper.readTree(result);
        assertThat(pimsNode.path("eventId").asText()).isEqualTo("evt-1");
        assertThat(pimsNode.path("aggregateId").asText()).isEqualTo("Imports.Notification.GBN-AG.GBN-AG-26-001");
        assertThat(pimsNode.path("aggregateVersion").asLong()).isEqualTo(1L);
        assertThat(pimsNode.path("eventType").asText())
            .isEqualTo("uk.gov.defra.imports.notification.NotificationSubmitted");
        assertThat(pimsNode.has("data")).isFalse();
        assertThat(pimsNode.has("actor")).isFalse();
        assertThat(pimsNode.has("metadata")).isFalse();
    }

    @Test
    void map_shouldThrowNonRetryable_whenBodyHasUnknownField() throws Exception {
        // Simulates a new field added to the backend's outbox event not yet in OutboxEvent
        String body = """
            {
              "eventId": "x",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "newFieldAddedByBackend": "some-value"
            }
            """;
        JsonNode node = objectMapper.readTree(body);

        // When / Then — @JsonIgnoreProperties(ignoreUnknown = false) causes a non-retryable failure
        assertThatThrownBy(() -> mapper.map(node))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Failed to map body to PimsEventV1");
    }

    @Test
    void map_shouldThrowNonRetryable_whenAggregateVersionIsWrongType() throws Exception {
        // aggregateVersion is a long — a string value fails Jackson deserialisation
        String body = """
            {
              "eventId": "x",
              "aggregateVersion": "not-a-number",
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted"
            }
            """;
        JsonNode node = objectMapper.readTree(body);

        assertThatThrownBy(() -> mapper.map(node))
            .isInstanceOf(SqsNonRetryableException.class);
    }

    @Test
    void map_shouldOmitNullFields_inOutputJson() throws Exception {
        // Given — only mandatory envelope fields present; all nullable fields absent in output
        String body = """
            {"eventId":"e1","aggregateVersion":0,"eventType":"uk.gov.defra.imports.notification.NotificationSubmitted"}
            """;
        JsonNode node = objectMapper.readTree(body);

        // When
        String result = mapper.map(node);
        JsonNode pimsNode = objectMapper.readTree(result);

        // Then — null scalar fields omitted (NON_NULL); null list fields serialise as []
        assertThat(pimsNode.has("actor")).isFalse();
        assertThat(pimsNode.has("data")).isFalse();
        assertThat(pimsNode.path("statusChanges").isArray()).isTrue();
        assertThat(pimsNode.path("statusChanges")).isEmpty();
    }
}
