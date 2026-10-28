package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Authentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Clause;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.DefinedContact;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ReferencedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV2;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsReferencedDocument;

class PimsEventMapperV2Test {

    private final PimsCommonMapperV2 commonMapper = new PimsCommonMapperV2();
    private final PimsEventMapperV2 mapper = new PimsEventMapperV2(
        new PimsGbnAgDataMapperV2(
            new PimsConsignmentMapperV2(
                new PimsTransportMapperV2(commonMapper),
                new PimsLineItemMapperV2(commonMapper),
                commonMapper
            ),
            commonMapper
        ),
        new PimsEnvelopeMapper()
    );

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final String EVENT_ID = "evt-1";
    private static final String EVENT_TYPE = "uk.gov.defra.imports.notification.NotificationSubmitted";

    @Test
    void map_shouldPassThroughAllEnvelopeFields() {
        OutboxEvent event = new OutboxEvent(
            EVENT_ID, "Notification", "GBN-AG",
            AGGREGATE_ID, 1L, EVENT_TYPE,
            NOW, null, null, null, null);

        PimsEventV2 result = mapper.map(event, PimsSchemaVersion.V0_2_0);

        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.aggregateType()).isEqualTo("Notification");
        assertThat(result.subType()).isEqualTo("GBN-AG");
        assertThat(result.aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(result.aggregateVersion()).isEqualTo(1L);
        assertThat(result.eventType()).isEqualTo(EVENT_TYPE);
        assertThat(result.timestamp()).isEqualTo(NOW);
    }

    @Test
    void map_shouldStampV2SchemaVersionAndUriAndKeepTheIncomingCorrelationId() {
        OutboxEventMetadata metadata = new OutboxEventMetadata("corr-1", "1.0", "https://schema.url");
        OutboxEvent event = eventWithMetadata(metadata);

        PimsEventV2 result = mapper.map(event, PimsSchemaVersion.V0_2_0);

        assertThat(result.metadata().correlationId()).isEqualTo("corr-1");
        assertThat(result.metadata().schemaVersion()).isEqualTo("0.2.0");
        assertThat(result.metadata().schemaUri()).isEqualTo(
            "https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.2.0.schema.json");
    }

    @Test
    void map_shouldHandleNullMetadata() {
        assertThat(mapper.map(minimalEvent(), PimsSchemaVersion.V0_2_0).metadata()).isNull();
    }

    @Test
    void map_shouldLeaveIssuerAndDocumentTypeListUrlOutOfV020() {
        var pimsDoc = mapper.map(eventWithIssuerAndDocument(), PimsSchemaVersion.V0_2_0).data().exchangedDocument();

        assertThat(pimsDoc.identifier()).isEqualTo("id-1");
        assertThat(pimsDoc.issuer()).isNull();
        assertThat(pimsDoc.referenceDocument()).containsExactly(
            new PimsReferencedDocument("GBN1", null, "GBHC1234567890", "2026-09-10"));
    }

    @Test
    void map_shouldSendIssuerAndDocumentTypeListUrlInV030() {
        var pimsDoc = mapper.map(eventWithIssuerAndDocument(), PimsSchemaVersion.V0_3_0).data().exchangedDocument();

        assertThat(pimsDoc.issuer().name()).isEqualTo("Animal and Plant Health Agency");
        assertThat(pimsDoc.issuer().postalAddress().lineOne()).isEqualTo("Woodham Lane");
        assertThat(pimsDoc.issuer().definedContact()).singleElement().satisfies(contact -> {
            assertThat(contact.emailURIUniversalCommunication()).isEqualTo("imports@apha.example.gov.uk");
            assertThat(contact.telephoneUniversalCommunication()).isEqualTo("03000 200301");
        });
        assertThat(pimsDoc.referenceDocument()).containsExactly(new PimsReferencedDocument(
            "GBN1", "https://refdata.tbc.defra.gov.uk/gbn-ag-document-types", "GBHC1234567890", "2026-09-10"));
    }

    @Test
    void map_shouldStampV030SchemaVersionAndUri() {
        OutboxEvent event = eventWithMetadata(new OutboxEventMetadata("corr-1", "1.0", "https://schema.url"));

        PimsEventV2 result = mapper.map(event, PimsSchemaVersion.V0_3_0);

        assertThat(result.metadata().schemaVersion()).isEqualTo("0.3.0");
        assertThat(result.metadata().schemaUri()).isEqualTo(
            "https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.3.0.schema.json");
    }

    private OutboxEvent eventWithIssuerAndDocument() {
        TradeParty issuer = new TradeParty(null, null, "Animal and Plant Health Agency", null, null,
            new TradeAddress("Woodham Lane", null, null, null, "GB", null, null),
            List.of(new DefinedContact(null, "03000 200301", "imports@apha.example.gov.uk")));
        ReferencedDocument refDoc = new ReferencedDocument(
            "GBN1", "https://refdata.tbc.defra.gov.uk/gbn-ag-document-types", "ZZZ", "GBHC1234567890", "2026-09-10");
        ExchangedDocument doc = new ExchangedDocument(
            "id-1", "trader-id", "SUBMITTED", 2, "2026-09-10", issuer, null, List.of(refDoc));
        return eventWithData(new GbnAgData("m", "t", doc, null));
    }

    @Test
    void map_shouldMapFirstSignatoryAuthenticationClauses() {
        // Given
        Clause attestation = new Clause("II.1", "The animals described above are healthy.",
            "https://refdata.tbc.defra.gov.uk/clause/II.1");
        Clause transport = new Clause("II.2", "Transport conditions were met.", null);
        ExchangedDocument doc = new ExchangedDocument(
            "id-1", "trader-id", "SUBMITTED", 2, "2026-09-10", null,
            new Authentication(List.of(attestation, transport)), null);
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", doc, null));

        // When
        var pimsAuth = mapper.map(event, PimsSchemaVersion.V0_2_0).data().exchangedDocument().firstSignatoryAuthentication();

        // Then
        assertThat(pimsAuth.includedClause()).hasSize(2);
        assertThat(pimsAuth.includedClause().getFirst().identifier()).isEqualTo("II.1");
        assertThat(pimsAuth.includedClause().getFirst().content())
            .isEqualTo("The animals described above are healthy.");
        assertThat(pimsAuth.includedClause().getFirst().urlId())
            .isEqualTo("https://refdata.tbc.defra.gov.uk/clause/II.1");
        assertThat(pimsAuth.includedClause().get(1).identifier()).isEqualTo("II.2");
        assertThat(pimsAuth.includedClause().get(1).urlId()).isNull();
    }

    @Test
    void map_shouldHandleNullFirstSignatoryAuthentication() {
        // Given
        ExchangedDocument doc = new ExchangedDocument(
            "id-1", "trader-id", "SUBMITTED", 2, "2026-09-10", null, null, null);
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", doc, null));

        // When / Then
        assertThat(mapper.map(event, PimsSchemaVersion.V0_2_0).data().exchangedDocument().firstSignatoryAuthentication())
            .isNull();
    }

    @Test
    void map_shouldMapApplicableClassificationAsArray_notTakeFirst() {
        // Given
        ApplicableClassification cn = new ApplicableClassification("CN", null,
            new CodedValue("0102", null, null), null);
        ApplicableClassification species = new ApplicableClassification("SPECIES_CLASS", null,
            new CodedValue("MAMMAL", null, null), null);
        TradeLineItem item = new TradeLineItem(List.of(cn, species), null, null, null, null, null, null, null, null);
        ConsignmentItem ci = new ConsignmentItem(List.of(item));
        SpecifiedConsignment sc = new SpecifiedConsignment(
            null, null, null, null, null, null, null, null, null, null, null, null, List.of(ci));
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When / Then
        var pimsItem = mapper.map(event, PimsSchemaVersion.V0_2_0).data().specifiedConsignment()
            .includedConsignmentItem().getFirst().includedTradeLineItem().getFirst();
        assertThat(pimsItem.applicableClassification()).hasSize(2);
        assertThat(pimsItem.applicableClassification().get(1).systemId()).isEqualTo("SPECIES_CLASS");
    }

    @Test
    void map_shouldHandleNullData() {
        assertThat(mapper.map(minimalEvent(), PimsSchemaVersion.V0_2_0).data()).isNull();
    }

    // --- Helpers ---

    private OutboxEvent minimalEvent() {
        return new OutboxEvent(EVENT_ID, "Notification", "GBN-AG",
            AGGREGATE_ID, 1L, EVENT_TYPE, NOW, null, null, null, null);
    }

    private OutboxEvent eventWithData(GbnAgData data) {
        return new OutboxEvent(EVENT_ID, "Notification", "GBN-AG",
            AGGREGATE_ID, 1L, EVENT_TYPE, NOW, data, null, null, null);
    }

    private OutboxEvent eventWithMetadata(OutboxEventMetadata metadata) {
        return new OutboxEvent(EVENT_ID, "Notification", "GBN-AG",
            AGGREGATE_ID, 1L, EVENT_TYPE, NOW, null, metadata, null, null);
    }
}
