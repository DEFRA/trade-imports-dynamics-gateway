package uk.gov.defra.cdp.dynamicsgateway.notification.outbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;

// Unknown fields cause a deserialisation failure — deliberate. When the backend adds a new
// field, update this model first, then decide in PimsEventMapper whether PIMS receives it.

/**
 * A java pojp representation of the outbox event.
 * Schema found in:
 * <a href="https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/events/gbn-ag-event-notification-created-v1.schema.json">trade-imports-schemas</a>
 */
@JsonIgnoreProperties()
public record OutboxEvent(
    String eventId,
    String aggregateType,
    String subType,
    String aggregateId,
    long aggregateVersion,
    String eventType,
    Instant timestamp,
    GbnAgData data,
    OutboxEventMetadata metadata,
    OutboxActor actor,
    List<OutboxStatusChange> statusChanges
) {}
