package uk.gov.defra.cdp.dynamicsgateway.notification;

import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxStatusChange;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsStatusChange;

@Component
class PimsEnvelopeMapper {

    PimsActor mapActor(OutboxActor actor) {
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

    PimsStatusChange mapStatusChange(OutboxStatusChange change) {
        if (change == null) return null;
        return new PimsStatusChange(change.status(), change.dateChanged(), mapActor(change.actor()));
    }

    PimsEventMetadata mapMetadata(OutboxEventMetadata metadata, String schemaVersion, String schemaUri) {
        if (metadata == null) return null;
        return new PimsEventMetadata(metadata.correlationId(), schemaVersion, schemaUri);
    }
}
