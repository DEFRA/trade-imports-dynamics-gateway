package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LogisticsPackage(
    Integer levelCode,
    String typeCode,
    Integer itemQuantity
) {}
