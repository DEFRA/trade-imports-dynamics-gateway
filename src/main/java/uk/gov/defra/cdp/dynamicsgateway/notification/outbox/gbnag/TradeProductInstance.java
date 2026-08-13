package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TradeProductInstance(
    String name,
    List<AnimalIdentifier> identifier,
    TradeParty permanentLocation
) {}
