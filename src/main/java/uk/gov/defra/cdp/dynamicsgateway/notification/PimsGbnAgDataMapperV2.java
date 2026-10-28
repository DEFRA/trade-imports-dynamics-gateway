package uk.gov.defra.cdp.dynamicsgateway.notification;

import static uk.gov.defra.cdp.dynamicsgateway.notification.PimsMapperSupport.mapList;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Authentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Clause;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsAuthentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsClause;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsGbnAgData;

@Component
@RequiredArgsConstructor
class PimsGbnAgDataMapperV2 {

    private final PimsConsignmentMapperV2 consignmentMapper;
    private final PimsCommonMapperV2 commonMapper;

    PimsGbnAgData mapData(GbnAgData data, PimsSchemaVersion version) {
        if (data == null) return null;
        return new PimsGbnAgData(
            data.model(),
            data.type(),
            mapExchangedDocument(data.exchangedDocument(), version),
            consignmentMapper.mapSpecifiedConsignment(data.specifiedConsignment(), version)
        );
    }

    private PimsExchangedDocument mapExchangedDocument(ExchangedDocument doc, PimsSchemaVersion version) {
        if (doc == null) return null;
        return new PimsExchangedDocument(
            doc.identifier(),
            doc.traderAssignedId(),
            doc.notificationStatusCode(),
            doc.versionId(),
            doc.issueDateTime(),
            version.isAtLeast(PimsSchemaVersion.V0_3_0) ? commonMapper.mapTradeParty(doc.issuer()) : null,
            mapAuthentication(doc.firstSignatoryAuthentication()),
            mapList(doc.referenceDocument(), referenceDocument -> commonMapper.mapReferencedDocument(referenceDocument, version))
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
}
