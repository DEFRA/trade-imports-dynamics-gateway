package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LogisticsLocation(
    String identifier,
    String urlId,
    String name,
    String typeCode,
    TradeAddress postalAddress
) {}
