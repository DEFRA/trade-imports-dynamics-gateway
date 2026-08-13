package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TransportEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTransportEvent;

@Component
class PimsTransportMapper {

    PimsLogisticsTransportMovement mapTransportMovement(LogisticsTransportMovement tm) {
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

    private <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return List.of();
        return list.stream().map(fn).toList();
    }
}
