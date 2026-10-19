package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeAddress(
    String lineOne,
    String lineTwo,
    String cityName,
    String countryId
) {}
