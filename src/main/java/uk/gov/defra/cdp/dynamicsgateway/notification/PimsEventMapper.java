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
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV1;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsStatusChange;

@Component
@RequiredArgsConstructor
class PimsEventMapper {

    private final PimsGbnAgDataMapper gbnAgDataMapper;

    PimsEventV1 map(OutboxEvent event) {
        return new PimsEventV1(
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
        return new PimsEventMetadata(metadata.correlationId(), metadata.schemaVersion(), metadata.schemaUrl());
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
