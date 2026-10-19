package uk.gov.defra.cdp.dynamicsgateway.notification;

import static uk.gov.defra.cdp.dynamicsgateway.notification.PimsMapperSupport.mapList;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountrySubDivision;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsSpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsTradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsTradeCountrySubDivision;

@Component
@RequiredArgsConstructor
class PimsConsignmentMapperV2 {

    private final PimsTransportMapperV2 transportMapper;
    private final PimsLineItemMapperV2 lineItemMapper;
    private final PimsCommonMapperV2 commonMapper;

    PimsSpecifiedConsignment mapSpecifiedConsignment(SpecifiedConsignment sc) {
        if (sc == null) return null;
        return new PimsSpecifiedConsignment(
            commonMapper.mapTradeParty(sc.consignorParty()),
            commonMapper.mapTradeParty(sc.consigneeParty()),
            commonMapper.mapTradeParty(sc.despatchParty()),
            commonMapper.mapTradeParty(sc.deliveryParty()),
            commonMapper.mapTradeParty(sc.importer()),
            commonMapper.mapTradeParty(sc.carrier()),
            mapTradeCountry(sc.originCountry()),
            commonMapper.mapLogisticsLocation(sc.unloadingBaseportLocation()),
            commonMapper.mapLogisticsLocation(sc.finalDestinationLocation()),
            mapList(sc.mainCarriageLogisticsTransportMovement(), transportMapper::mapTransportMovement),
            mapList(sc.transitTradeCountry(), this::mapTradeCountry),
            sc.isOrHasUnweanedAnimals(),
            mapList(sc.includedConsignmentItem(), lineItemMapper::mapConsignmentItem)
        );
    }

    // UNCL3227 location function 106 (the code list calls it "Region of origin"): here, the country
    // subdivision the goods came from. Not the region of origin code, which is a disease zone.
    private static final String SUBDIVISION_OF_ORIGIN = "106";

    private PimsTradeCountry mapTradeCountry(TradeCountry tc) {
        if (tc == null) return null;
        return new PimsTradeCountry(
            commonMapper.mapCodedValue(tc.code()),
            mapTradeCountrySubDivision(subdivisionOfOrigin(tc.subordinateTradeCountrySubDivision()))
        );
    }

    // The event carries every sub-division of a country; PIMS v0.2.0 takes one, the '106' entry.
    private static TradeCountrySubDivision subdivisionOfOrigin(List<TradeCountrySubDivision> subDivisions) {
        if (subDivisions == null) return null;
        return subDivisions.stream()
            .filter(sub -> sub.functionTypeCode() != null
                && SUBDIVISION_OF_ORIGIN.equals(sub.functionTypeCode().content()))
            .findFirst()
            .orElse(null);
    }

    private PimsTradeCountrySubDivision mapTradeCountrySubDivision(TradeCountrySubDivision sub) {
        if (sub == null) return null;
        return new PimsTradeCountrySubDivision(sub.identifier(), sub.urlId(),
            new PimsTradeCountrySubDivision.FunctionTypeCode(sub.functionTypeCode().content()));
    }
}
