package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsGbnAgData;

/**
 * A java pojp representation of the pims v0.1.0 event.
 * Schema found in:
 * <a href="https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.1.0.schema.json">trade-imports-schemas</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsEventV1(
    String eventId,
    String aggregateType,
    String subType,
    String aggregateId,
    long aggregateVersion,
    String eventType,
    Instant timestamp,
    PimsGbnAgData data,
    PimsEventMetadata metadata,
    PimsActor actor,
    List<PimsStatusChange> statusChanges
) {}
