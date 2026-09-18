package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsLogisticsLocation(
    String identifier,
    String urlId,
    String name,
    String typeCode,
    PimsTradeAddress postalAddress
) {}
