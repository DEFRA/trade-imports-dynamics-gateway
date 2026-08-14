package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsGbnAgData(
    @JsonProperty("$model") String model,
    @JsonProperty("$type") String type,
    PimsExchangedDocument exchangedDocument,
    PimsSpecifiedConsignment specifiedConsignment
) {}
