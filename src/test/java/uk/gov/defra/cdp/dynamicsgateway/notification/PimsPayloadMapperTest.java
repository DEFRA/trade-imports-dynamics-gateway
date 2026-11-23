package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;
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
        mapper = PimsPayloadMappers.real(objectMapper);
    }

    @Test
    void mapToV1_shouldRoundTripMinimalBody_toValidPimsPayload() throws Exception {
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

        String result = mapper.mapToV1(event);

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
    void parse_shouldReadOwningOrganisationId() throws Exception {
        assertThat(fixtureEvent().organisationId()).isEqualTo("5900001");
    }

    @Test
    void map_shouldIgnoreOwningOrganisationOnEveryPayload() throws Exception {
        OutboxEvent withOwner = fixtureEvent();
        ObjectNode withoutOwnerNode = (ObjectNode) fixtureJson().deepCopy();
        withoutOwnerNode.remove("organisationId");
        OutboxEvent withoutOwner = mapper.parse(withoutOwnerNode);
        List<Function<OutboxEvent, String>> everyVersion =
            List.of(mapper::mapToV1, mapper::mapToV2, mapper::mapToV3, mapper::mapToV4);

        for (Function<OutboxEvent, String> mapToVersion : everyVersion) {
            assertThat(objectMapper.readTree(mapToVersion.apply(withOwner)))
                .isEqualTo(objectMapper.readTree(mapToVersion.apply(withoutOwner)));
        }
    }

    @Test
    void parse_shouldThrowNonRetryable_whenBodyHasUnknownField() throws Exception {
        String body = """
            {
              "eventId": "x",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "newFieldAddedByBackend": "some-value"
            }
            """;
        JsonNode node = objectMapper.readTree(body);

        assertThatThrownBy(() -> mapper.parse(node))
            .isInstanceOf(SqsNonRetryableException.class)
            .hasMessageContaining("Failed to map body to OutboxEvent");
    }

    @Test
    void parse_shouldAcceptEverySubDivisionPropertyTheGenericSchemaAllows() throws Exception {
        String body = """
            {
              "eventId": "evt-sub",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "data": {
                "$model": "defra/certificate-internal/1",
                "$type": "gbn-ag",
                "specifiedConsignment": {
                  "originCountry": {
                    "code": { "value": "ES" },
                    "subordinateTradeCountrySubDivision": [
                      {
                        "identifier": "ES-CN",
                        "urlId": "https://refdata.tbc.defra.gov.uk/country_subdivisions",
                        "name": "Canary Islands",
                        "hierarchicalLevelCode": { "value": "1", "name": "Region" },
                        "functionTypeCode": { "content": "106", "name": "Region of Origin" }
                      }
                    ],
                    "regionOfOriginCode": "ES-12345"
                  }
                }
              }
            }
            """;

        OutboxEvent event = mapper.parse(objectMapper.readTree(body));

        var origin = event.data().specifiedConsignment().originCountry();
        assertThat(origin.regionOfOriginCode()).isEqualTo("ES-12345");
        assertThat(origin.subordinateTradeCountrySubDivision()).singleElement().satisfies(sub -> {
            assertThat(sub.name()).isEqualTo("Canary Islands");
            assertThat(sub.hierarchicalLevelCode().value()).isEqualTo("1");
            assertThat(sub.functionTypeCode().name()).isEqualTo("Region of Origin");
        });
    }

    @Test
    void mapToV1_shouldAcceptASubmittedEventAsTheBackendSendsIt() throws Exception {
        OutboxEvent event = fixtureEvent();

        String result = mapper.mapToV1(event);

        assertThat(objectMapper.readTree(result).path("data").path("exchangedDocument").has("referenceDocument"))
            .isFalse();
        JsonNode consignment = objectMapper.readTree(result).path("data").path("specifiedConsignment");
        assertThat(consignment.path("consignorParty").path("name").asText()).isEqualTo("Ferme Rosales");
        assertThat(consignment.path("carrier").path("identifier").asText()).isEqualTo("UK/TRANS/T1/00012345");
        assertThat(consignment.path("originCountry").path("code").path("value").asText()).isEqualTo("ES");
        assertThat(consignment.path("unloadingBaseportLocation").path("identifier").asText()).isEqualTo("GBDVR");
        assertThat(consignment.has("finalDestinationLocation")).isFalse();
        assertThat(consignment.has("transitTradeCountry")).isFalse();

        JsonNode lines = consignment.path("includedConsignmentItem").get(0).path("includedTradeLineItem");
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).has("description")).isFalse();
        assertThat(lines.get(0).has("scientificName")).isFalse();
        assertThat(lines.get(0).has("commonName")).isFalse();
        assertThat(lines.get(0).path("applicableClassification").isObject()).isTrue();
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
    void mapToV2_shouldProduceTheCommittedV020PayloadForTheBackendFixture() throws Exception {
        String result = mapper.mapToV2(fixtureEvent());

        try (InputStream in = getClass().getResourceAsStream("/gbn-ag/pims-v0.2.0-from-backend.json")) {
            assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree(in));
        }
    }

    @Test
    void mapToV3_shouldSendNoIssuerAndSendDocumentTypeListUrlAndRegionOfOriginCode() throws Exception {
        JsonNode result = objectMapper.readTree(mapper.mapToV3(fixtureEvent()));

        JsonNode exchangedDocument = result.path("data").path("exchangedDocument");
        assertThat(exchangedDocument.has("issuer")).isFalse();
        assertThat(exchangedDocument.path("referenceDocument").findValuesAsText("urlId"))
            .containsOnly("https://refdata.tbc.defra.gov.uk/gbn-ag-document-types")
            .hasSize(2);
        assertThat(exchangedDocument.path("firstSignatoryAuthentication").path("includedClause")
            .findValuesAsText("content")).containsExactly("INTERNAL_MARKET", "BREEDING", "FURTHER_KEEPING");
        assertThat(result.path("data").path("specifiedConsignment").path("originCountry")
            .path("regionOfOriginCode").asText()).isEqualTo("ES-12345");
    }

    @Test
    void map_shouldAcceptAnEventStillCarryingIssuerAndSendItInNoVersion() throws Exception {
        OutboxEvent event = mapper.parse(
            jsonResource("/gbn-ag/notification-submitted-from-backend-with-issuer.json"));
        List<Function<OutboxEvent, String>> everyVersion =
            List.of(mapper::mapToV1, mapper::mapToV2, mapper::mapToV3, mapper::mapToV4);

        for (Function<OutboxEvent, String> mapToVersion : everyVersion) {
            JsonNode exchangedDocument =
                objectMapper.readTree(mapToVersion.apply(event)).path("data").path("exchangedDocument");
            assertThat(exchangedDocument.path("identifier").asText()).isEqualTo("GBN-AG-26-7K8M2P");
            assertThat(exchangedDocument.has("issuer")).isFalse();
        }
    }

    @Test
    void mapToV4_shouldProduceTheCommittedV040PayloadForTheBackendFixture() throws Exception {
        String result = mapper.mapToV4(fixtureEvent());

        try (InputStream in = getClass().getResourceAsStream("/gbn-ag/pims-v0.4.0-from-backend.json")) {
            assertThat(objectMapper.readTree(result)).isEqualTo(objectMapper.readTree(in));
        }
    }

    @Test
    void mapToV4_shouldSendNoIssuerAndTheSameConsignmentAsV3() throws Exception {
        OutboxEvent event = fixtureEvent();

        JsonNode v4 = objectMapper.readTree(mapper.mapToV4(event));
        JsonNode v3 = objectMapper.readTree(mapper.mapToV3(event));

        assertThat(v4.path("data").path("exchangedDocument").has("issuer")).isFalse();
        assertThat(v4.path("data").path("exchangedDocument")).isEqualTo(v3.path("data").path("exchangedDocument"));
        assertThat(v4.path("data").path("specifiedConsignment"))
            .isEqualTo(v3.path("data").path("specifiedConsignment"));
    }

    @Test
    void mapToV2_shouldAcceptASubmittedEventAsTheBackendSendsIt() throws Exception {
        OutboxEvent event = fixtureEvent();

        String result = mapper.mapToV2(event);

        JsonNode referenceDocuments = objectMapper.readTree(result).path("data").path("exchangedDocument")
            .path("referenceDocument");
        assertThat(referenceDocuments).hasSize(2);
        assertThat(referenceDocuments.get(0).path("typeCode").asText()).isEqualTo("856");
        assertThat(referenceDocuments.get(0).path("identifier").asText()).isEqualTo("ITAHC-2026-0001");
        assertThat(referenceDocuments.get(0).path("issueDateTime").asText()).isEqualTo("2026-05-01");
        assertThat(referenceDocuments.get(0).has("urlId")).isFalse();
        assertThat(referenceDocuments.get(1).path("typeCode").asText()).isEqualTo("GBN1");
        JsonNode consignment = objectMapper.readTree(result).path("data").path("specifiedConsignment");
        assertThat(consignment.path("finalDestinationLocation").path("identifier").asText()).isEqualTo("123456789");
        assertThat(consignment.path("originCountry").path("subordinateTradeCountrySubDivision")
            .path("identifier").asText()).isEqualTo("ES-CN");
        assertThat(consignment.path("originCountry").has("regionOfOriginCode")).isFalse();
        assertThat(consignment.path("transitTradeCountry")).hasSize(2);
        assertThat(consignment.path("transitTradeCountry").get(0).path("code").path("value").asText())
            .isEqualTo("BE");
        assertThat(consignment.path("mainCarriageLogisticsTransportMovement").get(0)
            .path("transportContractRelatedReferencedDocument").get(0).path("identifier").asText())
            .isEqualTo("CMR-2026-884721");

        JsonNode lines = consignment.path("includedConsignmentItem").get(0).path("includedTradeLineItem");
        assertThat(lines.get(0).path("applicableClassification").isArray()).isTrue();
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
        String body = """
            {"eventId":"e1","aggregateVersion":0,"eventType":"uk.gov.defra.imports.notification.NotificationSubmitted"}
            """;
        OutboxEvent event = mapper.parse(objectMapper.readTree(body));

        String result = mapper.mapToV1(event);
        JsonNode pimsNode = objectMapper.readTree(result);

        assertThat(pimsNode.has("actor")).isFalse();
        assertThat(pimsNode.has("data")).isFalse();
        assertThat(pimsNode.path("statusChanges").isArray()).isTrue();
        assertThat(pimsNode.path("statusChanges")).isEmpty();
    }

    @Test
    void mapToEveryVersion_shouldEmitMetadataSchemaUri_notSchemaUrl() throws Exception {
        String body = """
            {
              "eventId": "evt-meta",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "metadata": {
                "correlationId": "cid-1",
                "schemaVersion": "1",
                "schemaUrl": "https://example.invalid/generic-side.schema.json"
              }
            }
            """;
        OutboxEvent event = mapper.parse(objectMapper.readTree(body));

        JsonNode v1Metadata = objectMapper.readTree(mapper.mapToV1(event)).path("metadata");
        JsonNode v2Metadata = objectMapper.readTree(mapper.mapToV2(event)).path("metadata");
        JsonNode v3Metadata = objectMapper.readTree(mapper.mapToV3(event)).path("metadata");
        JsonNode v4Metadata = objectMapper.readTree(mapper.mapToV4(event)).path("metadata");

        for (JsonNode metadata : List.of(v1Metadata, v2Metadata, v3Metadata, v4Metadata)) {
            assertThat(metadata.has("schemaUrl")).isFalse();
            assertThat(metadata.path("correlationId").asText()).isEqualTo("cid-1");
        }
        assertThat(v1Metadata.path("schemaUri").asText()).endsWith("gbn-ag-pims-v0.1.0.schema.json");
        assertThat(v2Metadata.path("schemaUri").asText()).endsWith("gbn-ag-pims-v0.2.0.schema.json");
        assertThat(v3Metadata.path("schemaUri").asText()).endsWith("gbn-ag-pims-v0.3.0.schema.json");
        assertThat(v4Metadata.path("schemaUri").asText()).endsWith("gbn-ag-pims-v0.4.0.schema.json");
        assertThat(v1Metadata.path("schemaVersion").asText()).isEqualTo("0.1.0");
        assertThat(v2Metadata.path("schemaVersion").asText()).isEqualTo("0.2.0");
        assertThat(v3Metadata.path("schemaVersion").asText()).isEqualTo("0.3.0");
        assertThat(v4Metadata.path("schemaVersion").asText()).isEqualTo("0.4.0");
    }

    @Test
    void mapToV2_shouldOmitApplicableClassification_whenUpstreamListIsAbsent() throws Exception {
        String body = """
            {
              "eventId": "evt-class",
              "aggregateVersion": 1,
              "eventType": "uk.gov.defra.imports.notification.NotificationSubmitted",
              "data": {
                "specifiedConsignment": {
                  "includedConsignmentItem": [
                    { "includedTradeLineItem": [ { "commonName": "Cow" } ] }
                  ]
                }
              }
            }
            """;
        OutboxEvent event = mapper.parse(objectMapper.readTree(body));

        String result = mapper.mapToV2(event);

        JsonNode line = objectMapper.readTree(result).path("data").path("specifiedConsignment")
            .path("includedConsignmentItem").get(0).path("includedTradeLineItem").get(0);
        assertThat(line.path("commonName").asText()).isEqualTo("Cow");
        assertThat(line.has("applicableClassification")).isFalse();
    }

    private OutboxEvent fixtureEvent() throws Exception {
        return mapper.parse(fixtureJson());
    }

    private JsonNode fixtureJson() throws Exception {
        return jsonResource("/gbn-ag/notification-submitted-from-backend.json");
    }

    private JsonNode jsonResource(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return objectMapper.readTree(in);
        }
    }
}
