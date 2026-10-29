package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeCountry(
    PimsCodedValue code,
    PimsTradeCountrySubDivision subordinateTradeCountrySubDivision
) {}
