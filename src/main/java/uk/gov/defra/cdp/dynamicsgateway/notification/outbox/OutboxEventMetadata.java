package uk.gov.defra.cdp.dynamicsgateway.notification.outbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record OutboxEventMetadata(
    String correlationId,
    String schemaVersion,
    String schemaUrl
) {}
