package uk.gov.defra.cdp.dynamicsgateway.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV4;

@Component
@RequiredArgsConstructor
class PimsEventMapperV4 {

    private static final String SCHEMA_VERSION = "0.4.0";
    private static final String SCHEMA_URI =
        "https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.4.0.schema.json";

    private final PimsGbnAgDataMapperV4 gbnAgDataMapper;
    private final PimsEnvelopeMapper envelopeMapper;

    PimsEventV4 map(OutboxEvent event) {
        return new PimsEventV4(
            event.eventId(),
            event.aggregateType(),
            event.subType(),
            event.aggregateId(),
            event.aggregateVersion(),
            event.eventType(),
            event.timestamp(),
            gbnAgDataMapper.mapData(event.data()),
            envelopeMapper.mapMetadata(event.metadata(), SCHEMA_VERSION, SCHEMA_URI),
            envelopeMapper.mapActor(event.actor()),
            PimsMapperSupport.mapList(event.statusChanges(), envelopeMapper::mapStatusChange)
        );
    }
}
