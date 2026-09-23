package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeCountrySubDivision(
    String identifier,
    String urlId,
    FunctionTypeCode functionTypeCode
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionTypeCode(String content) {}
}
