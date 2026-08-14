package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TradeAddress(
    String lineOne,
    String lineTwo,
    String cityName,
    String postcodeCode,
    String countryId,
    String countryName,
    String countrySubDivisionName
) {}
