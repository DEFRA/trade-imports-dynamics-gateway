package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.SqsNonRetryableException;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;

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
        PimsCommonMapperV2 commonMapper = new PimsCommonMapperV2();
        PimsEventMapperV2 pimsEventMapperV2 = new PimsEventMapperV2(
            new PimsGbnAgDataMapperV2(
                new PimsConsignmentMapperV2(
                    new PimsTransportMapperV2(commonMapper),
                    new PimsLineItemMapperV2(commonMapper),
                    commonMapper
                ),
                commonMapper
            )
        );
        mapper = new PimsPayloadMapper(objectMapper, pimsEventMapper, pimsEventMapperV2);
    }

    @Test
    void mapToV1_shouldRoundTripMinimalBody_toValidPimsPayload() throws Exception {
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
        OutboxEvent event = mapper.parse(objectMapper.readTree(body));

        // When
        String result = mapper.mapToV1(event);

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
    void parse_shouldThrowNonRetryable_whenBodyHasUnknownField() throws Exception {
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
        assertThatThrownBy(() -> mapper.parse(node))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Failed to map body to OutboxEvent");
    }

    @Test
    void mapToV1_shouldAcceptASubmittedEventAsTheBackendSendsIt() throws Exception {
        // Given — captured from the backend's GbnAgEventDataMapper for a submitted cow, dog and horse
        // notification, serialised the way its outbox does (nulls included). It carries the region
        // sub-division, CPH location, transit countries, transport document and per-animal
        // records, which must deserialise here even though v0.1.0 does not receive them.
        OutboxEvent event = fixtureEvent();

        // When
        String result = mapper.mapToV1(event);

        // Then — v0.1.0 output still drops exactly what it always has
        JsonNode consignment = objectMapper.readTree(result).path("data").path("specifiedConsignment");
        assertThat(consignment.path("consignorParty").path("name").asText()).isEqualTo("Ferme Rosales");
        assertThat(consignment.path("carrier").path("identifier").asText()).isEqualTo("UK/TRANS/T1/00012345");
        assertThat(consignment.path("originCountry").path("code").path("value").asText()).isEqualTo("FR");
        assertThat(consignment.path("unloadingBaseportLocation").path("identifier").asText()).isEqualTo("GBDVR");
        assertThat(consignment.has("finalDestinationLocation")).isFalse();
        assertThat(consignment.has("transitTradeCountry")).isFalse();

        JsonNode lines = consignment.path("includedConsignmentItem").get(0).path("includedTradeLineItem");
        assertThat(lines).hasSize(3); // cow, dog, horse
        assertThat(lines.get(0).has("description")).isFalse();
        assertThat(lines.get(0).has("scientificName")).isFalse();
        assertThat(lines.get(0).has("commonName")).isFalse();
        assertThat(lines.get(0).path("applicableClassification").isObject()).isTrue(); // still singular, not an array
        JsonNode cows = lines.get(0).path("individualTradeProductInstance");
        assertThat(cows).hasSize(2);
        assertThat(cows.get(0).path("identifier").get(0).path("content").asText()).isEqualTo("UK01234567890");
        assertThat(cows.get(1).path("identifier").get(0).path("content").asText()).isEqualTo("UK01234567891");
        assertThat(lines.get(1).path("individualTradeProductInstance").get(0).has("permanentLocation")).isFalse();
        JsonNode dogIdentifiers = lines.get(1).path("individualTradeProductInstance").get(0).path("identifier");
        assertThat(dogIdentifiers.get(2).path("typeCode").asText()).isEqualTo("TATTOO");
        assertThat(dogIdentifiers.get(2).path("content").asText()).isEqualTo("TT-4471");
    }

    @Test
    void mapToV2_shouldAcceptASubmittedEventAsTheBackendSendsIt() throws Exception {
        // Given — same real fixture as the v0.1.0 test above; v0.2.0 now carries the fields it drops
        OutboxEvent event = fixtureEvent();

        // When
        String result = mapper.mapToV2(event);

        // Then
        JsonNode consignment = objectMapper.readTree(result).path("data").path("specifiedConsignment");
        assertThat(consignment.path("finalDestinationLocation").path("identifier").asText()).isEqualTo("123456789");
        assertThat(consignment.path("originCountry").path("subordinateTradeCountrySubDivision")
            .path("identifier").asText()).isEqualTo("FR-75");
        assertThat(consignment.path("transitTradeCountry")).hasSize(2);
        assertThat(consignment.path("transitTradeCountry").get(0).path("code").path("value").asText())
            .isEqualTo("BE");
        assertThat(consignment.path("mainCarriageLogisticsTransportMovement").get(0)
            .path("transportContractRelatedReferencedDocument").get(0).path("identifier").asText())
            .isEqualTo("CMR-2026-884721");

        JsonNode lines = consignment.path("includedConsignmentItem").get(0).path("includedTradeLineItem");
        assertThat(lines.get(0).path("applicableClassification").isArray()).isTrue(); // array, not singular
        assertThat(lines.get(0).path("description").get(0).asText()).isEqualTo("Cow");
        assertThat(lines.get(0).path("scientificName").asText()).isEqualTo("Bos taurus");
        assertThat(lines.get(0).path("commonName").asText()).isEqualTo("Cow");

        JsonNode dogPermanentLocation = lines.get(1).path("individualTradeProductInstance").get(0)
            .path("permanentLocation");
        assertThat(dogPermanentLocation.path("name").asText()).isEqualTo("Brighton home");
        assertThat(dogPermanentLocation.path("postalAddress").path("cityName").asText()).isEqualTo("Brighton");
    }

    @Test
    void parse_shouldThrowNonRetryable_whenAggregateVersionIsWrongType() throws Exception {
        // aggregateVersion is a long — a string value fails Jackson deserialisation
        String body = """
            {
              "eventId": "x",
              "aggregateVersion": "not-a-number",
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted"
            }
            """;
        JsonNode node = objectMapper.readTree(body);

        assertThatThrownBy(() -> mapper.parse(node))
            .isInstanceOf(SqsNonRetryableException.class);
    }

    @Test
    void mapToV1_shouldOmitNullFields_inOutputJson() throws Exception {
        // Given — only mandatory envelope fields present; all nullable fields absent in output
        String body = """
            {"eventId":"e1","aggregateVersion":0,"eventType":"uk.gov.defra.imports.notification.NotificationSubmitted"}
            """;
        OutboxEvent event = mapper.parse(objectMapper.readTree(body));

        // When
        String result = mapper.mapToV1(event);
        JsonNode pimsNode = objectMapper.readTree(result);

        // Then — null scalar fields omitted (NON_NULL); null list fields serialise as []
        assertThat(pimsNode.has("actor")).isFalse();
        assertThat(pimsNode.has("data")).isFalse();
        assertThat(pimsNode.path("statusChanges").isArray()).isTrue();
        assertThat(pimsNode.path("statusChanges")).isEmpty();
    }

    private OutboxEvent fixtureEvent() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/gbn-ag/notification-submitted-from-backend.json")) {
            return mapper.parse(objectMapper.readTree(in));
        }
    }
}
