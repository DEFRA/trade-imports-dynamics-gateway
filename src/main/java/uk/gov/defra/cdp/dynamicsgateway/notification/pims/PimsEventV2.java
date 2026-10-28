package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsGbnAgData;

/**
 * The PIMS v0.2.0 and v0.3.0 event, as defined by
 * <a href="https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.2.0.schema.json">v0.2.0</a>
 * and
 * <a href="https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.3.0.schema.json">v0.3.0</a>
 * in trade-imports-schemas. Fields new in v0.3.0 are absent from a v0.2.0 event.
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
