package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxStatusChange;
import java.util.Collections;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.AnimalIdentifier;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Authentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Clause;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ProductUnitQuantity;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.DefinedContact;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ReferencedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountrySubDivision;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeProductInstance;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TransportEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV1;

class PimsEventMapperTest {

    private final PimsEventMapper mapper = new PimsEventMapper(
        new PimsGbnAgDataMapper(
            new PimsConsignmentMapper(
                new PimsTransportMapper(),
                new PimsLineItemMapper()
            )
        )
    );

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");
    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final String EVENT_ID = "evt-1";
    private static final String EVENT_TYPE = "uk.gov.defra.imports.notification.NotificationSubmitted";

    @Test
    void map_shouldPassThroughAllEnvelopeFields() {
        // Given
        OutboxEvent event = new OutboxEvent(
            EVENT_ID, "Notification", "GBN-AG",
            AGGREGATE_ID, 1L, EVENT_TYPE,
            NOW, null, null, null, null);

        // When
        PimsEventV1 result = mapper.map(event);

        // Then
        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.aggregateType()).isEqualTo("Notification");
        assertThat(result.subType()).isEqualTo("GBN-AG");
        assertThat(result.aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(result.aggregateVersion()).isEqualTo(1L);
        assertThat(result.eventType()).isEqualTo(EVENT_TYPE);
        assertThat(result.timestamp()).isEqualTo(NOW);
        assertThat(result.data()).isNull();
        assertThat(result.metadata()).isNull();
        assertThat(result.actor()).isNull();
        assertThat(result.statusChanges()).isEmpty();
    }

    @Test
    void map_shouldMapActorFields() {
        // Given
        OutboxActor actor = new OutboxActor("u1", "defra-id", "INTERNAL", "Test User", "org-1", null);
        OutboxEvent event = eventWithActor(actor);

        // When
        PimsEventV1 result = mapper.map(event);

        // Then
        assertThat(result.actor().id()).isEqualTo("u1");
        assertThat(result.actor().source()).isEqualTo("defra-id");
        assertThat(result.actor().userType()).isEqualTo("INTERNAL");
        assertThat(result.actor().displayName()).isEqualTo("Test User");
        assertThat(result.actor().organisationId()).isEqualTo("org-1");
        assertThat(result.actor().onBehalfOfOrganisationId()).isNull();
    }

    @Test
    void map_shouldHandleNullActor() {
        assertThat(mapper.map(minimalEvent()).actor()).isNull();
    }

    @Test
    void map_shouldMapMetadata() {
        // Given
        OutboxEventMetadata metadata = new OutboxEventMetadata("corr-1", "1.0", "https://schema.url");
        OutboxEvent event = eventWithMetadata(metadata);

        // When
        PimsEventV1 result = mapper.map(event);

        // Then
        assertThat(result.metadata().correlationId()).isEqualTo("corr-1");
        assertThat(result.metadata().schemaVersion()).isEqualTo("1.0");
        assertThat(result.metadata().schemaUrl()).isEqualTo("https://schema.url");
    }

    @Test
    void map_shouldHandleNullMetadata() {
        assertThat(mapper.map(minimalEvent()).metadata()).isNull();
    }

    @Test
    void map_shouldMapStatusChanges() {
        // Given
        OutboxActor actor = new OutboxActor("u1", "defra-id", "INTERNAL", "Test User", "org-1", null);
        OutboxStatusChange change = new OutboxStatusChange("SUBMITTED", NOW, actor);
        OutboxEvent event = eventWithStatusChanges(List.of(change));

        // When
        PimsEventV1 result = mapper.map(event);

        // Then
        assertThat(result.statusChanges()).hasSize(1);
        assertThat(result.statusChanges().getFirst().status()).isEqualTo("SUBMITTED");
        assertThat(result.statusChanges().getFirst().dateChanged()).isEqualTo(NOW);
        assertThat(result.statusChanges().getFirst().actor().id()).isEqualTo("u1");
    }

    @Test
    void map_shouldHandleNullStatusChanges() {
        assertThat(mapper.map(minimalEvent()).statusChanges()).isEmpty();
    }

    @Test
    void map_shouldHandleEmptyStatusChanges() {
        // Given
        OutboxEvent event = eventWithStatusChanges(List.of());

        // When / Then
        assertThat(mapper.map(event).statusChanges()).isEmpty();
    }

    @Test
    void map_shouldMapGbnAgData() {
        // Given
        GbnAgData data = new GbnAgData("gbn-ag-model", "gbn-ag-type", null, null);
        OutboxEvent event = eventWithData(data);

        // When
        PimsEventV1 result = mapper.map(event);

        // Then
        assertThat(result.data().model()).isEqualTo("gbn-ag-model");
        assertThat(result.data().type()).isEqualTo("gbn-ag-type");
        assertThat(result.data().exchangedDocument()).isNull();
        assertThat(result.data().specifiedConsignment()).isNull();
    }

    @Test
    void map_shouldHandleNullData() {
        assertThat(mapper.map(minimalEvent()).data()).isNull();
    }

    @Test
    void map_shouldMapExchangedDocument_andDropIssuerAndReferenceDocument() {
        // Given
        Authentication auth = new Authentication(List.of(new Clause("c1", "content", "url")));
        ExchangedDocument doc = new ExchangedDocument(
            "id-1", "trader-id", "SUBMITTED", 2, "2026-08-12", null, auth, null);
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", doc, null));

        // When
        PimsEventV1 result = mapper.map(event);

        // Then — issuer and referenceDocument dropped; firstSignatoryAuthentication mapped
        var pimsDoc = result.data().exchangedDocument();
        assertThat(pimsDoc.identifier()).isEqualTo("id-1");
        assertThat(pimsDoc.traderAssignedId()).isEqualTo("trader-id");
        assertThat(pimsDoc.notificationStatusCode()).isEqualTo("SUBMITTED");
        assertThat(pimsDoc.versionId()).isEqualTo(2);
        assertThat(pimsDoc.issueDateTime()).isEqualTo("2026-08-12");
        assertThat(pimsDoc.firstSignatoryAuthentication().includedClause()).hasSize(1);
        assertThat(pimsDoc.firstSignatoryAuthentication().includedClause().getFirst().identifier()).isEqualTo("c1");
    }

    @Test
    void map_shouldMapTradeParty_andDropRoleCodeAndDefinedContact() {
        // Given
        CodedValue roleCode = new CodedValue("IMPORTER", "url", "name");
        CodedValue typeCode = new CodedValue("TC", "tcUrl", "tcName");
        TradeAddress addr = new TradeAddress("Line 1", null, "London", "SW1A 1AA", "GB", "United Kingdom", null);
        TradeParty party = new TradeParty("p-1", "pUrl", "Party Name", roleCode, List.of(typeCode), addr, null);
        SpecifiedConsignment sc = emptyConsignment().withConsignorParty(party).build();
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When
        var pimsParty = mapper.map(event).data().specifiedConsignment().consignorParty();

        // Then — partyRoleCode dropped; postalAddress mapped with 4 fields only
        assertThat(pimsParty.identifier()).isEqualTo("p-1");
        assertThat(pimsParty.name()).isEqualTo("Party Name");
        assertThat(pimsParty.partyTypeCode()).hasSize(1);
        assertThat(pimsParty.partyTypeCode().getFirst().value()).isEqualTo("TC");
        assertThat(pimsParty.partyTypeCode().getFirst().urlId()).isEqualTo("tcUrl");
        assertThat(pimsParty.postalAddress().lineOne()).isEqualTo("Line 1");
        assertThat(pimsParty.postalAddress().cityName()).isEqualTo("London");
        assertThat(pimsParty.postalAddress().countryId()).isEqualTo("GB");
    }

    @Test
    void map_shouldMapApplicableClassification_takingFirstFromList() {
        // Given — backend produces a list; PIMS receives a singular (PR #52)
        CodedValue classCode = new CodedValue("0102", "url", "Bovine");
        ApplicableClassification ac = new ApplicableClassification("HS", "Harmonised System", classCode, List.of("Cattle"));
        TradeLineItem item = new TradeLineItem(List.of(ac), null, null, null, null, null, null, null, null);
        ConsignmentItem ci = new ConsignmentItem(List.of(item));
        SpecifiedConsignment sc = emptyConsignment().withIncludedConsignmentItem(List.of(ci)).build();
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When / Then — first applicableClassification taken; systemName and className dropped
        var pimsItem = mapper.map(event).data().specifiedConsignment().includedConsignmentItem().getFirst()
            .includedTradeLineItem().getFirst();
        assertThat(pimsItem.applicableClassification().systemId()).isEqualTo("HS");
        assertThat(pimsItem.applicableClassification().classCode().value()).isEqualTo("0102");
    }

    @Test
    void map_shouldReturnNullClassification_whenApplicableClassificationListIsEmpty() {
        // Given
        TradeLineItem item = new TradeLineItem(List.of(), null, null, null, null, null, null, null, null);
        ConsignmentItem ci = new ConsignmentItem(List.of(item));
        SpecifiedConsignment sc = emptyConsignment().withIncludedConsignmentItem(List.of(ci)).build();
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When / Then
        var pimsItem = mapper.map(event).data().specifiedConsignment().includedConsignmentItem().getFirst()
            .includedTradeLineItem().getFirst();
        assertThat(pimsItem.applicableClassification()).isNull();
    }

    @Test
    void map_shouldMapTransportMovement_andDropIdentifierUrlIdAndReferencedDocuments() {
        // Given
        LogisticsTransportMeans means = new LogisticsTransportMeans("vessel");
        TransportEvent te = new TransportEvent(NOW, null, null);
        LogisticsTransportMovement tm = new LogisticsTransportMovement("id", "url", 1, means, null, List.of(te));
        SpecifiedConsignment sc = emptyConsignment().withMainCarriageTransport(List.of(tm)).build();
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When
        var pimsTm = mapper.map(event).data().specifiedConsignment()
            .mainCarriageLogisticsTransportMovement().getFirst();

        // Then — identifier and urlId dropped; arrivalEvent mapped with scheduledOccurrenceDateTime only
        assertThat(pimsTm.modeCode()).isEqualTo(1);
        assertThat(pimsTm.usedLogisticsTransportMeans().name()).isEqualTo("vessel");
        assertThat(pimsTm.arrivalEvent()).hasSize(1);
        assertThat(pimsTm.arrivalEvent().getFirst().scheduledOccurrenceDateTime()).isEqualTo(NOW);
    }

    @Test
    void map_shouldMapProductUnitQuantity_andDropUnitCode() {
        // Given
        ProductUnitQuantity qty = new ProductUnitQuantity(5, "HEAD");
        LineTradeDelivery delivery = new LineTradeDelivery(qty);
        TradeLineItem item = new TradeLineItem(null, null, null, null, null, null, List.of(delivery), null, null);
        ConsignmentItem ci = new ConsignmentItem(List.of(item));
        SpecifiedConsignment sc = emptyConsignment().withIncludedConsignmentItem(List.of(ci)).build();
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When / Then
        var pimsDelivery = mapper.map(event).data().specifiedConsignment().includedConsignmentItem().getFirst()
            .includedTradeLineItem().getFirst().specifiedLineTradeDelivery().getFirst();
        assertThat(pimsDelivery.productUnitQuantity().content()).isEqualTo(5);
    }

    @Test
    void map_shouldMapLogisticsPackage_andDropLevelCodeAndTypeCode() {
        // Given
        LogisticsPackage pkg = new LogisticsPackage(1, "BOX", 10);
        TradeLineItem item = new TradeLineItem(null, null, null, null, null, null, null, List.of(pkg), null);
        ConsignmentItem ci = new ConsignmentItem(List.of(item));
        SpecifiedConsignment sc = emptyConsignment().withIncludedConsignmentItem(List.of(ci)).build();
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When / Then
        var pimsPkg = mapper.map(event).data().specifiedConsignment().includedConsignmentItem().getFirst()
            .includedTradeLineItem().getFirst().physicalReferencedLogisticsPackage().getFirst();
        assertThat(pimsPkg.itemQuantity()).isEqualTo(10);
    }

    @Test
    void map_shouldMapAnimalIdentifier_andDropUrlId() {
        // Given
        AnimalIdentifier id = new AnimalIdentifier("EAR_TAG", "UK123456", "urlId");
        TradeProductInstance instance = new TradeProductInstance(null, List.of(id), null);
        TradeLineItem item = new TradeLineItem(null, null, null, null, null, null, null, null, List.of(instance));
        ConsignmentItem ci = new ConsignmentItem(List.of(item));
        SpecifiedConsignment sc = emptyConsignment().withIncludedConsignmentItem(List.of(ci)).build();
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When / Then
        var pimsId = mapper.map(event).data().specifiedConsignment().includedConsignmentItem().getFirst()
            .includedTradeLineItem().getFirst().individualTradeProductInstance().getFirst()
            .identifier().getFirst();
        assertThat(pimsId.typeCode()).isEqualTo("EAR_TAG");
        assertThat(pimsId.content()).isEqualTo("UK123456");
    }

    @Test
    void map_shouldMapOriginCountryAndUnloadingBaseportLocation() {
        // Given — exercises mapTradeCountry and mapLogisticsLocation non-null paths,
        // and constructs TradeCountry, TradeCountrySubDivision, LogisticsLocation records
        TradeCountry origin = new TradeCountry(
            new CodedValue("GB", "url", null),
            new TradeCountrySubDivision("ENG", null, new TradeCountrySubDivision.FunctionTypeCode("106")));
        LogisticsLocation port = new LogisticsLocation("PORT-1", null, null, null, null);
        SpecifiedConsignment sc = new SpecifiedConsignment(
            null, null, null, null, null, null, origin, port, null, null, null, null, null);
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When
        var pimsConsignment = mapper.map(event).data().specifiedConsignment();

        // Then
        assertThat(pimsConsignment.originCountry().code().value()).isEqualTo("GB");
        assertThat(pimsConsignment.unloadingBaseportLocation().identifier()).isEqualTo("PORT-1");
    }

    @Test
    void map_shouldHandleNullCodeInTradeCountry() {
        // Given — TradeCountry with null code exercises mapCodedValue null branch
        TradeCountry origin = new TradeCountry(null, null);
        SpecifiedConsignment sc = new SpecifiedConsignment(
            null, null, null, null, null, null, origin, null, null, null, null, null, null);
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When / Then
        assertThat(mapper.map(event).data().specifiedConsignment().originCountry().code()).isNull();
    }

    @Test
    void map_shouldHandleNullPostalAddressOnTradeParty() {
        // Given — TradeParty with null postalAddress exercises mapTradeAddress null branch
        TradeParty party = new TradeParty("p-1", null, "Name", null, null, null, null);
        SpecifiedConsignment sc = emptyConsignment().withConsignorParty(party).build();
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", null, sc));

        // When / Then
        assertThat(mapper.map(event).data().specifiedConsignment().consignorParty().postalAddress()).isNull();
    }

    @Test
    void map_shouldHandleNullAuthenticationInExchangedDocument() {
        // Given — null firstSignatoryAuthentication exercises mapAuthentication null branch
        ExchangedDocument doc = new ExchangedDocument("id", null, null, null, null, null, null, null);
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", doc, null));

        // When / Then
        assertThat(mapper.map(event).data().exchangedDocument().firstSignatoryAuthentication()).isNull();
    }

    @Test
    void map_shouldHandleNullIncludedClauseList() {
        // Given — Authentication with null includedClause exercises mapList null branch in PimsGbnAgDataMapper
        ExchangedDocument doc = new ExchangedDocument("id", null, null, null, null, null,
            new Authentication(null), null);
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", doc, null));

        // When / Then
        assertThat(mapper.map(event).data().exchangedDocument().firstSignatoryAuthentication().includedClause()).isEmpty();
    }

    @Test
    void map_shouldHandleNullClauseElementInAuthentication() {
        // Given — null element in includedClause list exercises mapClause null branch
        ExchangedDocument doc = new ExchangedDocument("id", null, null, null, null, null,
            new Authentication(Collections.singletonList(null)), null);
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", doc, null));

        // When / Then — null clause element maps to null in output list
        assertThat(mapper.map(event).data().exchangedDocument().firstSignatoryAuthentication().includedClause()).hasSize(1);
        assertThat(mapper.map(event).data().exchangedDocument().firstSignatoryAuthentication().includedClause().getFirst()).isNull();
    }

    @Test
    void map_shouldConstructDroppedInputTypes() {
        DefinedContact contact = new DefinedContact("Person", "+44123", "email@test.com");
        ReferencedDocument refDoc = new ReferencedDocument("T", "R", "ref-1", "2026-08-13");
        TradeParty party = new TradeParty("p-1", null, null, null, null, null, List.of(contact));
        ExchangedDocument doc = new ExchangedDocument("id", null, null, null, null, null,
            new Authentication(List.of(new Clause("c1", "text", null))), List.of(refDoc));
        OutboxEvent event = eventWithData(new GbnAgData("m", "t", doc,
            emptyConsignment().withConsignorParty(party).build()));

        // When / Then — mapper runs without error; dropped fields absent from PIMS output
        assertThat(mapper.map(event).data().exchangedDocument().identifier()).isEqualTo("id");
        assertThat(mapper.map(event).data().specifiedConsignment().consignorParty().identifier()).isEqualTo("p-1");
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

    private OutboxEvent eventWithActor(OutboxActor actor) {
        return new OutboxEvent(EVENT_ID, "Notification", "GBN-AG",
            AGGREGATE_ID, 1L, EVENT_TYPE, NOW, null, null, actor, null);
    }

    private OutboxEvent eventWithMetadata(OutboxEventMetadata metadata) {
        return new OutboxEvent(EVENT_ID, "Notification", "GBN-AG",
            AGGREGATE_ID, 1L, EVENT_TYPE, NOW, null, metadata, null, null);
    }

    private OutboxEvent eventWithStatusChanges(List<OutboxStatusChange> changes) {
        return new OutboxEvent(EVENT_ID, "Notification", "GBN-AG",
            AGGREGATE_ID, 1L, EVENT_TYPE, NOW, null, null, null, changes);
    }

    private SpecifiedConsignmentBuilder emptyConsignment() {
        return new SpecifiedConsignmentBuilder();
    }

    private static class SpecifiedConsignmentBuilder {
        private TradeParty consignorParty;
        private List<LogisticsTransportMovement> mainCarriageTransport;
        private List<ConsignmentItem> includedConsignmentItem;

        SpecifiedConsignmentBuilder withConsignorParty(TradeParty p) {
            this.consignorParty = p;
            return this;
        }

        SpecifiedConsignmentBuilder withMainCarriageTransport(List<LogisticsTransportMovement> tms) {
            this.mainCarriageTransport = tms;
            return this;
        }

        SpecifiedConsignmentBuilder withIncludedConsignmentItem(List<ConsignmentItem> items) {
            this.includedConsignmentItem = items;
            return this;
        }

        SpecifiedConsignment build() {
            return new SpecifiedConsignment(consignorParty, null, null, null, null, null,
                null, null, null, mainCarriageTransport, null, null, includedConsignmentItem);
        }

    }
}
