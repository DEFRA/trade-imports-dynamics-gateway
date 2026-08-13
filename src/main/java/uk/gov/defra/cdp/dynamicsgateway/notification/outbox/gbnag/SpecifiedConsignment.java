package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SpecifiedConsignment(
    TradeParty consignorParty,
    TradeParty consigneeParty,
    TradeParty despatchParty,
    TradeParty deliveryParty,
    TradeParty importer,
    TradeParty carrier,
    TradeCountry originCountry,
    LogisticsLocation unloadingBaseportLocation,
    List<LogisticsTransportMovement> mainCarriageLogisticsTransportMovement,
    List<TradeCountry> transitTradeCountry,
    Boolean isOrHasUnweanedAnimals,
    List<ConsignmentItem> includedConsignmentItem
) {}
