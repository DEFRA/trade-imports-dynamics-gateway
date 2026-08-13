package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsEventMetadata(
    String correlationId,
    String schemaVersion,
    String schemaUrl
) {}
