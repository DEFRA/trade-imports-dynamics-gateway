package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.SpecifiedConsignment;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;

class PimsConsignmentMapperTest {

    private final PimsConsignmentMapper mapper = new PimsConsignmentMapper(
        new PimsTransportMapper(), new PimsLineItemMapper());

    @Test
    void mapSpecifiedConsignment_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapSpecifiedConsignment(null)).isNull();
    }

    @Test
    void mapTradeParty_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapTradeParty(null)).isNull();
    }

    @Test
    void mapTradeParty_shouldHandleNullPostalAddress() {
        // Given — null postalAddress exercises mapTradeAddress null branch
        var party = new TradeParty("p-1", null, "Name", null, null, null, null);

        // When / Then
        assertThat(mapper.mapTradeParty(party).postalAddress()).isNull();
    }

    @Test
    void mapTradeParty_shouldHandleNullPartyTypeCodeList() {
        // Given — null partyTypeCode exercises mapList null branch → returns empty list
        var party = new TradeParty("p-1", null, null, null, null, null, null);

        // When / Then
        assertThat(mapper.mapTradeParty(party).partyTypeCode()).isEmpty();
    }

    @Test
    void mapTradeParty_shouldHandleNullCodedValueElementInPartyTypeCode() {
        // Given — null element in partyTypeCode exercises mapCodedValue null branch
        var party = new TradeParty("p-1", null, null, null,
            Collections.singletonList(null), null, null);

        // When / Then
        assertThat(mapper.mapTradeParty(party).partyTypeCode()).hasSize(1);
        assertThat(mapper.mapTradeParty(party).partyTypeCode().getFirst()).isNull();
    }

    @Test
    void mapSpecifiedConsignment_shouldHandleAllNullPartyFields() {
        // Given — every party field null; exercises mapTradeParty null branch for each
        var sc = new SpecifiedConsignment(
            null, null, null, null, null, null, null, null, null, null, null, null);

        // When
        var result = mapper.mapSpecifiedConsignment(sc);

        // Then — all party fields null in output
        assertThat(result.consignorParty()).isNull();
        assertThat(result.consigneeParty()).isNull();
        assertThat(result.originCountry()).isNull();
        assertThat(result.unloadingBaseportLocation()).isNull();
        assertThat(result.mainCarriageLogisticsTransportMovement()).isEmpty();
        assertThat(result.includedConsignmentItem()).isEmpty();
    }

    @Test
    void mapTradeParty_shouldHandleTradeAddressWithAllNullFields() {
        // Given — TradeAddress with all nullable fields null
        var addr = new TradeAddress(null, null, null, null, null, null, null);
        var party = new TradeParty("p-1", null, null, null, null, addr, null);

        // When / Then
        var pimsAddr = mapper.mapTradeParty(party).postalAddress();
        assertThat(pimsAddr.lineOne()).isNull();
        assertThat(pimsAddr.countryId()).isNull();
    }

    @Test
    void mapTradeParty_shouldHandleNullCodedValueInList() {
        // Given — non-null CodedValue maps correctly
        var cv = new CodedValue("IMPORTER", "url", "dropped-name");
        var party = new TradeParty("p-1", null, null, null, List.of(cv), null, null);

        // When / Then — name is dropped (PR #52), value and urlId pass through
        var pimsParty = mapper.mapTradeParty(party);
        assertThat(pimsParty.partyTypeCode().getFirst().value()).isEqualTo("IMPORTER");
        assertThat(pimsParty.partyTypeCode().getFirst().urlId()).isEqualTo("url");
    }
}
