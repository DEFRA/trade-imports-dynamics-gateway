package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = false)
public record GbnAgData(
    @JsonProperty("$model") String model,
    @JsonProperty("$type") String type,
    ExchangedDocument exchangedDocument,
    SpecifiedConsignment specifiedConsignment
) {}
