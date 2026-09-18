package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxStatusChange;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV2;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsStatusChange;

@Component
@RequiredArgsConstructor
class PimsEventMapperV2 {

    // Stamped, not read from the incoming event's own metadata — PIMS distinguishes the two
    // simultaneously-published streams by these values, confirmed with PIMS (EUDPA-370).
    private static final String SCHEMA_VERSION = "0.2.0";
    private static final String SCHEMA_URL =
        "https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.2.0.schema.json";

    private final PimsGbnAgDataMapperV2 gbnAgDataMapper;

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
            mapMetadata(event.metadata()),
            mapActor(event.actor()),
            mapList(event.statusChanges(), this::mapStatusChange)
        );
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

    private PimsEventMetadata mapMetadata(OutboxEventMetadata metadata) {
        if (metadata == null) return null;
        return new PimsEventMetadata(metadata.correlationId(), SCHEMA_VERSION, SCHEMA_URL);
    }

    private PimsStatusChange mapStatusChange(OutboxStatusChange change) {
        if (change == null) return null;
        return new PimsStatusChange(change.status(), change.dateChanged(), mapActor(change.actor()));
    }

    private <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return List.of();
        return list.stream().map(fn).toList();
    }
}
