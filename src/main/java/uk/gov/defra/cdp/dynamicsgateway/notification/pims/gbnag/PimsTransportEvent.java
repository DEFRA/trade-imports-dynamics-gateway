package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTransportEvent(
    Instant scheduledOccurrenceDateTime
) {}
