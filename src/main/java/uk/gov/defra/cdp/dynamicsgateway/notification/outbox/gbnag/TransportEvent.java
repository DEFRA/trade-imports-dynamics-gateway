package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TransportEvent(
    Instant scheduledOccurrenceDateTime,
    Instant actualOccurrenceDateTime,
    LogisticsLocation occurrenceLogisticsLocation
) {}
