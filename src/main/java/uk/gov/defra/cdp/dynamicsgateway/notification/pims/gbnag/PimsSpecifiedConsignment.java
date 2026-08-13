package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// transitTradeCountry omitted — always null in v0.1.0 (PR #52)
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
    List<PimsLogisticsTransportMovement> mainCarriageLogisticsTransportMovement,
    Boolean isOrHasUnweanedAnimals,
    List<PimsConsignmentItem> includedConsignmentItem
) {}
