package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;
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

    private PimsTradeCountry mapTradeCountry(TradeCountry tc) {
        if (tc == null) return null;
        return new PimsTradeCountry(
            commonMapper.mapCodedValue(tc.code()),
            mapTradeCountrySubDivision(tc.subordinateTradeCountrySubDivision())
        );
    }

    private PimsTradeCountrySubDivision mapTradeCountrySubDivision(TradeCountrySubDivision sub) {
        if (sub == null) return null;
        PimsTradeCountrySubDivision.FunctionTypeCode functionTypeCode = sub.functionTypeCode() == null
            ? null
            : new PimsTradeCountrySubDivision.FunctionTypeCode(sub.functionTypeCode().content());
        return new PimsTradeCountrySubDivision(sub.identifier(), sub.urlId(), functionTypeCode);
    }

    private <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return List.of();
        return list.stream().map(fn).toList();
    }
}
