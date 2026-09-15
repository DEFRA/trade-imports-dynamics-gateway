package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TradeCountrySubDivision(
    String identifier,
    String urlId,
    FunctionTypeCode functionTypeCode
) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record FunctionTypeCode(String content) {}
}
