package uk.gov.defra.cdp.dynamicsgateway.notification.outbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = false)
public record OutboxStatusChange(
    String status,
    Instant dateChanged,
    OutboxActor actor
) {}
