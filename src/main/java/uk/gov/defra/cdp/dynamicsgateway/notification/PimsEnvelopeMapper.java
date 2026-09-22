package uk.gov.defra.cdp.dynamicsgateway.notification;

import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxStatusChange;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsActor;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsStatusChange;

/**
 * Envelope-level mapping shared by {@link PimsEventMapper} (v0.1.0) and {@link PimsEventMapperV2}
 * (v0.2.0).
 *
 * <p>The envelope records — {@link PimsActor}, {@link PimsStatusChange}, {@link PimsEventMetadata} —
 * are version-neutral: both streams emit the same shapes, so the mapping is identical and belongs in
 * one place. This mirrors {@link PimsCommonMapperV2}, which does the same job for the v0.2.0 leaf
 * shapes, and is kept dependency-free for the same reason.
 *
 * <p>Only the two stamped metadata constants differ between streams, so
 * {@link #mapMetadata(OutboxEventMetadata, String, String)} takes them as arguments and each event
 * mapper keeps its own.
 */
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

    /**
     * Builds the PIMS metadata block, stamping the caller's schema version and URL rather than
     * passing through the incoming event's own values — PIMS distinguishes the two
     * simultaneously-published streams by them.
     */
    PimsEventMetadata mapMetadata(OutboxEventMetadata metadata, String schemaVersion, String schemaUrl) {
        if (metadata == null) return null;
        return new PimsEventMetadata(metadata.correlationId(), schemaVersion, schemaUrl);
    }
}
