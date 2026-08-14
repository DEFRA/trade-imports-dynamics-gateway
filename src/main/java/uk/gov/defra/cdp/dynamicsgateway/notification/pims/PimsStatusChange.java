package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsStatusChange(
    String status,
    Instant dateChanged,
    PimsActor actor
) {}
