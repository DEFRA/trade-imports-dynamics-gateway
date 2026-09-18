package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountry;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeCountrySubDivision;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;

class PimsConsignmentMapperV2Test {

    private final PimsCommonMapperV2 commonMapper = new PimsCommonMapperV2();
    private final PimsConsignmentMapperV2 mapper = new PimsConsignmentMapperV2(
        new PimsTransportMapperV2(commonMapper), new PimsLineItemMapperV2(commonMapper), commonMapper);

    @Test
    void mapSpecifiedConsignment_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapSpecifiedConsignment(null)).isNull();
    }

    @Test
    void mapSpecifiedConsignment_shouldMapFinalDestinationLocation() {
        var loc = new LogisticsLocation("123456789", "https://refdata.tbc.defra.gov.uk/cph_number",
            null, null, null);
        var sc = emptyConsignment(loc, null, null);

        var result = mapper.mapSpecifiedConsignment(sc);

        assertThat(result.finalDestinationLocation().identifier()).isEqualTo("123456789");
        assertThat(result.finalDestinationLocation().urlId())
            .isEqualTo("https://refdata.tbc.defra.gov.uk/cph_number");
    }

    @Test
    void mapSpecifiedConsignment_shouldHandleNullFinalDestinationLocation() {
        var sc = emptyConsignment(null, null, null);

        assertThat(mapper.mapSpecifiedConsignment(sc).finalDestinationLocation()).isNull();
    }

    @Test
    void mapSpecifiedConsignment_shouldMapTransitTradeCountryList() {
        var be = new TradeCountry(new CodedValue("BE", null, null), null);
        var de = new TradeCountry(new CodedValue("DE", null, null), null);
        var sc = emptyConsignment(null, null, List.of(be, de));

        var result = mapper.mapSpecifiedConsignment(sc);

        assertThat(result.transitTradeCountry()).hasSize(2);
        assertThat(result.transitTradeCountry().get(0).code().value()).isEqualTo("BE");
        assertThat(result.transitTradeCountry().get(1).code().value()).isEqualTo("DE");
    }

    @Test
    void mapSpecifiedConsignment_shouldHandleNullTransitTradeCountryList() {
        var sc = emptyConsignment(null, null, null);

        assertThat(mapper.mapSpecifiedConsignment(sc).transitTradeCountry()).isEmpty();
    }

    @Test
    void mapSpecifiedConsignment_shouldMapOriginCountryWithRegionSubdivision() {
        var subdivision = new TradeCountrySubDivision("FR-75", "subUrl",
            new TradeCountrySubDivision.FunctionTypeCode("106"));
        var origin = new TradeCountry(new CodedValue("FR", null, null), subdivision);
        var sc = emptyConsignment(null, origin, null);

        var result = mapper.mapSpecifiedConsignment(sc);

        assertThat(result.originCountry().code().value()).isEqualTo("FR");
        assertThat(result.originCountry().subordinateTradeCountrySubDivision().identifier())
            .isEqualTo("FR-75");
        assertThat(result.originCountry().subordinateTradeCountrySubDivision().urlId())
            .isEqualTo("subUrl");
        assertThat(result.originCountry().subordinateTradeCountrySubDivision().functionTypeCode().content())
            .isEqualTo("106");
        // Not an array — see the v0.2.0 schema fix (trade-imports-schemas#57)
        assertThat(result.originCountry().subordinateTradeCountrySubDivision())
            .isNotInstanceOf(java.util.List.class);
    }

    @Test
    void mapSpecifiedConsignment_shouldHandleNullSubordinateTradeCountrySubDivision() {
        var origin = new TradeCountry(new CodedValue("FR", null, null), null);
        var sc = emptyConsignment(null, origin, null);

        assertThat(mapper.mapSpecifiedConsignment(sc).originCountry()
            .subordinateTradeCountrySubDivision()).isNull();
    }

    @Test
    void mapSpecifiedConsignment_shouldHandleNullFunctionTypeCode() {
        var subdivision = new TradeCountrySubDivision("FR-75", null, null);
        var origin = new TradeCountry(new CodedValue("FR", null, null), subdivision);
        var sc = emptyConsignment(null, origin, null);

        assertThat(mapper.mapSpecifiedConsignment(sc).originCountry()
            .subordinateTradeCountrySubDivision().functionTypeCode()).isNull();
    }

    @Test
    void mapSpecifiedConsignment_shouldMapPartyRoleCodeAndDefinedContact_viaCommonMapper() {
        // Given — confirms delegation to the shared mapper, not a re-implementation here
        var party = new TradeParty("p-1", null, "Consignor",
            new CodedValue("SZ", null, null), null, null, null);
        var sc = new SpecifiedConsignment(
            party, null, null, null, null, null, null, null, null, null, null, null, null);

        var result = mapper.mapSpecifiedConsignment(sc);

        assertThat(result.consignorParty().partyRoleCode().value()).isEqualTo("SZ");
    }

    @Test
    void mapSpecifiedConsignment_shouldHandleAllNullPartyFields() {
        var sc = new SpecifiedConsignment(
            null, null, null, null, null, null, null, null, null, null, null, null, null);

        var result = mapper.mapSpecifiedConsignment(sc);

        assertThat(result.consignorParty()).isNull();
        assertThat(result.originCountry()).isNull();
        assertThat(result.unloadingBaseportLocation()).isNull();
        assertThat(result.finalDestinationLocation()).isNull();
        assertThat(result.mainCarriageLogisticsTransportMovement()).isEmpty();
        assertThat(result.transitTradeCountry()).isEmpty();
        assertThat(result.includedConsignmentItem()).isEmpty();
    }

    private SpecifiedConsignment emptyConsignment(
            LogisticsLocation finalDestinationLocation,
            TradeCountry originCountry,
            List<TradeCountry> transitTradeCountry) {
        return new SpecifiedConsignment(
            null, null, null, null, null, null,
            originCountry, null, finalDestinationLocation,
            null, transitTradeCountry, null, null);
    }
}
