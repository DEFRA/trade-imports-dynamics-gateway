package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsGbnAgData;

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
