package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsCodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsSpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeParty;

@Component
@RequiredArgsConstructor
class PimsConsignmentMapper {

    private final PimsTransportMapper transportMapper;
    private final PimsLineItemMapper lineItemMapper;

    PimsSpecifiedConsignment mapSpecifiedConsignment(SpecifiedConsignment sc) {
        if (sc == null) return null;
        // transitTradeCountry omitted — always null in v0.1.0 (PR #52)
        return new PimsSpecifiedConsignment(
            mapTradeParty(sc.consignorParty()),
            mapTradeParty(sc.consigneeParty()),
            mapTradeParty(sc.despatchParty()),
            mapTradeParty(sc.deliveryParty()),
            mapTradeParty(sc.importer()),
            mapTradeParty(sc.carrier()),
            mapTradeCountry(sc.originCountry()),
            mapLogisticsLocation(sc.unloadingBaseportLocation()),
            mapList(sc.mainCarriageLogisticsTransportMovement(), transportMapper::mapTransportMovement),
            sc.isOrHasUnweanedAnimals(),
            mapList(sc.includedConsignmentItem(), lineItemMapper::mapConsignmentItem)
        );
    }

    PimsTradeParty mapTradeParty(TradeParty party) {
        if (party == null) return null;
        // partyRoleCode and definedContact omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeParty(
            party.identifier(),
            party.urlId(),
            party.name(),
            mapList(party.partyTypeCode(), this::mapCodedValue),
            mapTradeAddress(party.postalAddress())
        );
    }

    private PimsTradeAddress mapTradeAddress(TradeAddress addr) {
        if (addr == null) return null;
        // postcodeCode, countryName, countrySubDivisionName omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeAddress(addr.lineOne(), addr.lineTwo(), addr.cityName(), addr.countryId());
    }

    private PimsCodedValue mapCodedValue(CodedValue cv) {
        if (cv == null) return null;
        // name omitted — always null in v0.1.0 (PR #52)
        return new PimsCodedValue(cv.value(), cv.urlId());
    }

    private PimsTradeCountry mapTradeCountry(TradeCountry tc) {
        if (tc == null) return null;
        // subordinateTradeCountrySubDivision omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeCountry(mapCodedValue(tc.code()));
    }

    private PimsLogisticsLocation mapLogisticsLocation(LogisticsLocation loc) {
        if (loc == null) return null;
        // urlId, name, typeCode, postalAddress omitted — always null in v0.1.0 (PR #52)
        return new PimsLogisticsLocation(loc.identifier());
    }

    private <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return List.of();
        return list.stream().map(fn).toList();
    }
}
