package uk.gov.defra.cdp.dynamicsgateway.notification;

import static uk.gov.defra.cdp.dynamicsgateway.notification.PimsMapperSupport.mapList;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv4.PimsExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv4.PimsGbnAgData;

@Component
@RequiredArgsConstructor
class PimsGbnAgDataMapperV4 {

    private final PimsConsignmentMapperV3 consignmentMapper;
    private final PimsCommonMapperV3 commonMapper;

    PimsGbnAgData mapData(GbnAgData data) {
        if (data == null) return null;
        return new PimsGbnAgData(
            data.model(),
            data.type(),
            mapExchangedDocument(data.exchangedDocument()),
            consignmentMapper.mapSpecifiedConsignment(data.specifiedConsignment())
        );
    }

    private PimsExchangedDocument mapExchangedDocument(ExchangedDocument doc) {
        if (doc == null) return null;
        return new PimsExchangedDocument(
            doc.identifier(),
            doc.traderAssignedId(),
            doc.notificationStatusCode(),
            doc.versionId(),
            doc.issueDateTime(),
            commonMapper.mapAuthentication(doc.firstSignatoryAuthentication()),
            mapList(doc.referenceDocument(), commonMapper::mapReferencedDocument)
        );
    }
}
