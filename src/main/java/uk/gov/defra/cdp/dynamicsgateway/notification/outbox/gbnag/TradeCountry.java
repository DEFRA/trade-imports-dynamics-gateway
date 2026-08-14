package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TradeCountry(
    CodedValue code,
    TradeCountrySubDivision subordinateTradeCountrySubDivision
) {}
