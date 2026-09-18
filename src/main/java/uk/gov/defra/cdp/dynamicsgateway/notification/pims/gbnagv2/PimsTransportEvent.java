package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTransportEvent(
    Instant scheduledOccurrenceDateTime,
    Instant actualOccurrenceDateTime,
    PimsLogisticsLocation occurrenceLogisticsLocation
) {}
