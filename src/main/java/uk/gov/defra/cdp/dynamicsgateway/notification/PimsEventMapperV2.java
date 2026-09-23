package uk.gov.defra.cdp.dynamicsgateway.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV2;

@Component
@RequiredArgsConstructor
class PimsEventMapperV2 {

    // Stamped, not read from the incoming event's own metadata — PIMS distinguishes the two
    // simultaneously-published streams by these values, confirmed with PIMS (EUDPA-370).
    private static final String SCHEMA_VERSION = "0.2.0";
    private static final String SCHEMA_URL =
        "https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.2.0.schema.json";

    private final PimsGbnAgDataMapperV2 gbnAgDataMapper;
    private final PimsEnvelopeMapper envelopeMapper;

    PimsEventV2 map(OutboxEvent event) {
        return new PimsEventV2(
            event.eventId(),
            event.aggregateType(),
            event.subType(),
            event.aggregateId(),
            event.aggregateVersion(),
            event.eventType(),
            event.timestamp(),
            gbnAgDataMapper.mapData(event.data()),
            envelopeMapper.mapMetadata(event.metadata(), SCHEMA_VERSION, SCHEMA_URL),
            envelopeMapper.mapActor(event.actor()),
            PimsMapperSupport.mapList(event.statusChanges(), envelopeMapper::mapStatusChange)
        );
    }
}
