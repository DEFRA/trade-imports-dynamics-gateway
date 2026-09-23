package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.DefinedContact;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeLineItem;
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
    }

    @Test
    void mapSpecifiedConsignment_shouldSerialiseSubdivisionAsObject_notArray() throws Exception {
        // Guards the v0.2.0 schema fix (trade-imports-schemas#57), which corrected
        // subordinateTradeCountrySubDivision from an array to a single object. This has to be
        // asserted on the serialised JSON: the accessor is statically typed
        // PimsTradeCountrySubDivision, so no assertion on the Java object can tell the two
        // shapes apart.
        var subdivision = new TradeCountrySubDivision("FR-75", "subUrl",
            new TradeCountrySubDivision.FunctionTypeCode("106"));
        var origin = new TradeCountry(new CodedValue("FR", null, null), subdivision);
        var sc = emptyConsignment(null, origin, null);

        var json = new ObjectMapper().valueToTree(mapper.mapSpecifiedConsignment(sc));

        JsonNode node = json.path("originCountry").path("subordinateTradeCountrySubDivision");
        assertThat(node.isObject()).isTrue();
        assertThat(node.isArray()).isFalse();
        assertThat(node.path("identifier").asText()).isEqualTo("FR-75");
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
        var contact = new DefinedContact("Marie Rosales", "+33 1 23 45 67 89", "marie@example.invalid");
        var party = new TradeParty("p-1", null, "Consignor",
            new CodedValue("SZ", null, null), null, null, List.of(contact));
        var sc = new SpecifiedConsignment(
            party, null, null, null, null, null, null, null, null, null, null, null, null);

        var result = mapper.mapSpecifiedConsignment(sc);

        assertThat(result.consignorParty().partyRoleCode().value()).isEqualTo("SZ");
        assertThat(result.consignorParty().definedContact()).hasSize(1);
        assertThat(result.consignorParty().definedContact().get(0).personName())
            .isEqualTo("Marie Rosales");
        assertThat(result.consignorParty().definedContact().get(0).telephoneUniversalCommunication())
            .isEqualTo("+33 1 23 45 67 89");
        assertThat(result.consignorParty().definedContact().get(0).emailURIUniversalCommunication())
            .isEqualTo("marie@example.invalid");
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

    @Test
    void mapSpecifiedConsignment_shouldMapTransportMovementAndConsignmentItem_viaDelegateMappers() {
        // Given — the only other fields that delegate to PimsTransportMapperV2 and
        // PimsLineItemMapperV2. Previously exercised on the null path only, so a broken or
        // dropped wire-through would not have failed any test.
        var movement = new LogisticsTransportMovement(
            "UK/TRANS/T1/00012345", "https://refdata.tbc.defra.gov.uk/transport", 3,
            new LogisticsTransportMeans("MV ATLANTIC STAR"), null, null);
        var lineItem = new TradeLineItem(
            null, List.of("Cow"), "Bos taurus", "Cow", null, null, null, null, null);
        var sc = new SpecifiedConsignment(
            null, null, null, null, null, null, null, null, null,
            List.of(movement), null, null, List.of(new ConsignmentItem(List.of(lineItem))));

        var result = mapper.mapSpecifiedConsignment(sc);

        assertThat(result.mainCarriageLogisticsTransportMovement()).hasSize(1);
        assertThat(result.mainCarriageLogisticsTransportMovement().get(0).identifier())
            .isEqualTo("UK/TRANS/T1/00012345");
        assertThat(result.mainCarriageLogisticsTransportMovement().get(0).modeCode()).isEqualTo(3);

        assertThat(result.includedConsignmentItem()).hasSize(1);
        var mappedLines = result.includedConsignmentItem().get(0).includedTradeLineItem();
        assertThat(mappedLines).hasSize(1);
        assertThat(mappedLines.get(0).commonName()).isEqualTo("Cow");
        assertThat(mappedLines.get(0).scientificName()).isEqualTo("Bos taurus");
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
