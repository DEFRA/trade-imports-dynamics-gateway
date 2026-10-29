package uk.gov.defra.cdp.dynamicsgateway.notification;

import static uk.gov.defra.cdp.dynamicsgateway.notification.PimsMapperSupport.mapList;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TransportEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsLogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsLogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsTransportEvent;

@Component
@RequiredArgsConstructor
class PimsTransportMapperV2 {

    private final PimsCommonMapperV2 commonMapper;

    PimsLogisticsTransportMovement mapTransportMovement(LogisticsTransportMovement tm) {
        if (tm == null) return null;
        return new PimsLogisticsTransportMovement(
            tm.identifier(),
            tm.urlId(),
            tm.modeCode(),
            mapTransportMeans(tm.usedLogisticsTransportMeans()),
            mapList(tm.transportContractRelatedReferencedDocument(), commonMapper::mapReferencedDocument),
            mapList(tm.arrivalEvent(), this::mapTransportEvent)
        );
    }

    private PimsLogisticsTransportMeans mapTransportMeans(LogisticsTransportMeans means) {
        if (means == null) return null;
        return new PimsLogisticsTransportMeans(means.name());
    }

    private PimsTransportEvent mapTransportEvent(TransportEvent event) {
        if (event == null) return null;
        return new PimsTransportEvent(
            event.scheduledOccurrenceDateTime(),
            event.actualOccurrenceDateTime(),
            commonMapper.mapLogisticsLocation(event.occurrenceLogisticsLocation())
        );
    }
}
