package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxStatusChange;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Authentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Clause;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ProductUnitQuantity;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeProductInstance;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TransportEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV1;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsStatusChange;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsAnimalIdentifier;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsAuthentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsClause;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsCodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsGbnAgData;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsProductUnitQuantity;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsSpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeParty;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeProductInstance;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTransportEvent;

@Component
class PimsEventMapper {

    PimsEventV1 map(OutboxEvent event) {
        return new PimsEventV1(
            event.eventId(),
            event.aggregateType(),
            event.subType(),
            event.aggregateId(),
            event.aggregateVersion(),
            event.eventType(),
            event.timestamp(),
            mapData(event.data()),
            mapMetadata(event.metadata()),
            mapActor(event.actor()),
            mapList(event.statusChanges(), this::mapStatusChange)
        );
    }

    private PimsGbnAgData mapData(GbnAgData data) {
        if (data == null) return null;
        return new PimsGbnAgData(
            data.model(),
            data.type(),
            mapExchangedDocument(data.exchangedDocument()),
            mapSpecifiedConsignment(data.specifiedConsignment())
        );
    }

    private PimsExchangedDocument mapExchangedDocument(ExchangedDocument doc) {
        if (doc == null) return null;
        // issuer and referenceDocument omitted — always null in v0.1.0 (PR #52)
        return new PimsExchangedDocument(
            doc.identifier(),
            doc.traderAssignedId(),
            doc.notificationStatusCode(),
            doc.versionId(),
            doc.issueDateTime(),
            mapAuthentication(doc.firstSignatoryAuthentication())
        );
    }

    private PimsAuthentication mapAuthentication(Authentication auth) {
        if (auth == null) return null;
        return new PimsAuthentication(mapList(auth.includedClause(), this::mapClause));
    }

    private PimsClause mapClause(Clause clause) {
        if (clause == null) return null;
        return new PimsClause(clause.identifier(), clause.content(), clause.urlId());
    }

    private PimsSpecifiedConsignment mapSpecifiedConsignment(SpecifiedConsignment sc) {
        if (sc == null) return null;
        // transitTradeCountry omitted — always null in v0.1.0 (PR #52)
        return new PimsSpecifiedConsignment(
            mapTradeParty(sc.consignorParty()),
            mapTradeParty(sc.consigneeParty()),
            mapTradeParty(sc.despatchParty()),
            mapTradeParty(sc.deliveryParty()),
            mapTradeParty(sc.importer()),
            mapTradeParty(sc.carrier()),
            mapTradeCountry(sc.originCountry()),
            mapLogisticsLocation(sc.unloadingBaseportLocation()),
            mapList(sc.mainCarriageLogisticsTransportMovement(), this::mapTransportMovement),
            sc.isOrHasUnweanedAnimals(),
            mapList(sc.includedConsignmentItem(), this::mapConsignmentItem)
        );
    }

    private PimsTradeParty mapTradeParty(TradeParty party) {
        if (party == null) return null;
        // partyRoleCode and definedContact omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeParty(
            party.identifier(),
            party.urlId(),
            party.name(),
            mapList(party.partyTypeCode(), this::mapCodedValue),
            mapTradeAddress(party.postalAddress())
        );
    }

    private uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeAddress mapTradeAddress(
            uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeAddress addr) {
        if (addr == null) return null;
        // postcodeCode, countryName, countrySubDivisionName omitted — always null in v0.1.0 (PR #52)
        return new uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeAddress(
            addr.lineOne(),
            addr.lineTwo(),
            addr.cityName(),
            addr.countryId()
        );
    }

    private PimsCodedValue mapCodedValue(CodedValue cv) {
        if (cv == null) return null;
        // name omitted — always null in v0.1.0 (PR #52)
        return new PimsCodedValue(cv.value(), cv.urlId());
    }

    private PimsTradeCountry mapTradeCountry(TradeCountry tc) {
        if (tc == null) return null;
        // subordinateTradeCountrySubDivision omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeCountry(mapCodedValue(tc.code()));
    }

    private PimsLogisticsLocation mapLogisticsLocation(LogisticsLocation loc) {
        if (loc == null) return null;
        // urlId, name, typeCode, postalAddress omitted — always null in v0.1.0 (PR #52)
        return new PimsLogisticsLocation(loc.identifier());
    }

    private PimsLogisticsTransportMovement mapTransportMovement(LogisticsTransportMovement tm) {
        if (tm == null) return null;
        // identifier, urlId, transportContractRelatedReferencedDocument omitted — always null in v0.1.0 (PR #52)
        return new PimsLogisticsTransportMovement(
            tm.modeCode(),
            mapTransportMeans(tm.usedLogisticsTransportMeans()),
            mapList(tm.arrivalEvent(), this::mapTransportEvent)
        );
    }

    private PimsLogisticsTransportMeans mapTransportMeans(LogisticsTransportMeans means) {
        if (means == null) return null;
        return new PimsLogisticsTransportMeans(means.name());
    }

    private PimsTransportEvent mapTransportEvent(TransportEvent event) {
        if (event == null) return null;
        // actualOccurrenceDateTime and occurrenceLogisticsLocation omitted — always null in v0.1.0 (PR #52)
        return new PimsTransportEvent(event.scheduledOccurrenceDateTime());
    }

    private PimsConsignmentItem mapConsignmentItem(ConsignmentItem item) {
        if (item == null) return null;
        return new PimsConsignmentItem(mapList(item.includedTradeLineItem(), this::mapTradeLineItem));
    }

    private PimsTradeLineItem mapTradeLineItem(TradeLineItem item) {
        if (item == null) return null;
        // applicableClassification: backend always produces exactly one — take first (PR #52)
        PimsApplicableClassification classification = item.applicableClassification() == null
            || item.applicableClassification().isEmpty()
            ? null
            : mapApplicableClassification(item.applicableClassification().getFirst());
        // description, scientificName, commonName, typeCode, urlId omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeLineItem(
            classification,
            mapList(item.specifiedLineTradeDelivery(), this::mapLineTradeDelivery),
            mapList(item.physicalReferencedLogisticsPackage(), this::mapLogisticsPackage),
            mapList(item.individualTradeProductInstance(), this::mapTradeProductInstance)
        );
    }

    private PimsApplicableClassification mapApplicableClassification(ApplicableClassification ac) {
        if (ac == null) return null;
        // systemName and className omitted — always null in v0.1.0 (PR #52)
        return new PimsApplicableClassification(ac.systemId(), mapCodedValue(ac.classCode()));
    }

    private PimsLineTradeDelivery mapLineTradeDelivery(LineTradeDelivery delivery) {
        if (delivery == null) return null;
        return new PimsLineTradeDelivery(mapProductUnitQuantity(delivery.productUnitQuantity()));
    }

    private PimsProductUnitQuantity mapProductUnitQuantity(ProductUnitQuantity qty) {
        if (qty == null) return null;
        // unitCode omitted — always null in v0.1.0 (PR #52)
        return new PimsProductUnitQuantity(qty.content());
    }

    private PimsLogisticsPackage mapLogisticsPackage(LogisticsPackage pkg) {
        if (pkg == null) return null;
        // levelCode and typeCode omitted — always null in v0.1.0 (PR #52)
        return new PimsLogisticsPackage(pkg.itemQuantity());
    }

    private PimsTradeProductInstance mapTradeProductInstance(TradeProductInstance instance) {
        if (instance == null) return null;
        // name and permanentLocation omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeProductInstance(
            mapList(instance.identifier(), this::mapAnimalIdentifier)
        );
    }

    private PimsAnimalIdentifier mapAnimalIdentifier(
            uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.AnimalIdentifier id) {
        if (id == null) return null;
        // urlId omitted — always null in v0.1.0 (PR #52)
        return new PimsAnimalIdentifier(id.typeCode(), id.content());
    }

    private PimsActor mapActor(OutboxActor actor) {
        if (actor == null) return null;
        return new PimsActor(
            actor.id(),
            actor.source(),
            actor.userType(),
            actor.displayName(),
            actor.organisationId(),
            actor.onBehalfOfOrganisationId()
        );
    }

    private PimsEventMetadata mapMetadata(
            uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEventMetadata metadata) {
        if (metadata == null) return null;
        return new PimsEventMetadata(
            metadata.correlationId(),
            metadata.schemaVersion(),
            metadata.schemaUrl()
        );
    }

    private PimsStatusChange mapStatusChange(OutboxStatusChange change) {
        if (change == null) return null;
        return new PimsStatusChange(change.status(), change.dateChanged(), mapActor(change.actor()));
    }

    private <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return null;
        return list.stream().map(fn).toList();
    }
}
