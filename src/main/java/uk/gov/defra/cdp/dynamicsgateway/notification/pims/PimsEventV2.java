package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsGbnAgData;

/**
 * A java pojo representation of the pims v0.2.0 event.
 * Schema found in:
 * <a href="https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.2.0.schema.json">trade-imports-schemas</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsEventV2(
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
