package uk.gov.defra.cdp.dynamicsgateway.notification;

import static uk.gov.defra.cdp.dynamicsgateway.notification.PimsMapperSupport.mapList;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountrySubDivision;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv3.PimsSpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv3.PimsTradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv3.PimsTradeCountrySubDivision;

@Component
@RequiredArgsConstructor
class PimsConsignmentMapperV3 {

    private final PimsTransportMapperV3 transportMapper;
    private final PimsLineItemMapperV3 lineItemMapper;
    private final PimsCommonMapperV3 commonMapper;

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
            mapTradeCountrySubDivision(subdivisionOfOrigin(tc.subordinateTradeCountrySubDivision())),
            tc.regionOfOriginCode()
        );
    }

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
