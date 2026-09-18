package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsSpecifiedConsignment(
    PimsTradeParty consignorParty,
    PimsTradeParty consigneeParty,
    PimsTradeParty despatchParty,
    PimsTradeParty deliveryParty,
    PimsTradeParty importer,
    PimsTradeParty carrier,
    PimsTradeCountry originCountry,
    PimsLogisticsLocation unloadingBaseportLocation,
    PimsLogisticsLocation finalDestinationLocation,
    List<PimsLogisticsTransportMovement> mainCarriageLogisticsTransportMovement,
    List<PimsTradeCountry> transitTradeCountry,
    Boolean isOrHasUnweanedAnimals,
    List<PimsConsignmentItem> includedConsignmentItem
) {}
