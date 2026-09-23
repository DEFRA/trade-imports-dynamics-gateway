package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.DefinedContact;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ReferencedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;

class PimsCommonMapperV2Test {

    private final PimsCommonMapperV2 mapper = new PimsCommonMapperV2();

    @Test
    void mapCodedValue_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapCodedValue(null)).isNull();
    }

    @Test
    void mapCodedValue_shouldMapAllThreeFields() {
        var result = mapper.mapCodedValue(new CodedValue("IMPORTER", "url", "Importer"));

        assertThat(result.value()).isEqualTo("IMPORTER");
        assertThat(result.urlId()).isEqualTo("url");
        assertThat(result.name()).isEqualTo("Importer");
    }

    @Test
    void mapTradeParty_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapTradeParty(null)).isNull();
    }

    @Test
    void mapTradeParty_shouldMapPartyRoleCodeAndDefinedContact() {
        var party = new TradeParty(
            "p-1", "pUrl", "Party Name",
            new CodedValue("CA", "roleUrl", "Carrier"),
            List.of(new CodedValue("TC", "tcUrl", "tcName")),
            new TradeAddress("Line 1", null, "London", "SW1A 1AA", "GB", "United Kingdom", "Greater London"),
            List.of(new DefinedContact("Jane Doe", "0123456789", "jane@example.com"))
        );

        var result = mapper.mapTradeParty(party);

        assertThat(result.partyRoleCode().value()).isEqualTo("CA");
        assertThat(result.definedContact()).hasSize(1);
        assertThat(result.definedContact().getFirst().personName()).isEqualTo("Jane Doe");
        assertThat(result.definedContact().getFirst().emailURIUniversalCommunication()).isEqualTo("jane@example.com");
        assertThat(result.postalAddress().postcodeCode()).isEqualTo("SW1A 1AA");
        assertThat(result.postalAddress().countryName()).isEqualTo("United Kingdom");
        assertThat(result.postalAddress().countrySubDivisionName()).isEqualTo("Greater London");
    }

    @Test
    void mapTradeParty_shouldHandleNullPartyRoleCodeAndNullDefinedContactList() {
        var party = new TradeParty("p-1", null, null, null, null, null, null);

        var result = mapper.mapTradeParty(party);

        assertThat(result.partyRoleCode()).isNull();
        assertThat(result.definedContact()).isEmpty();
    }

    @Test
    void mapTradeParty_shouldHandleNullDefinedContactElement() {
        var party = new TradeParty("p-1", null, null, null, null, null,
            Collections.singletonList(null));

        var result = mapper.mapTradeParty(party);

        assertThat(result.definedContact()).hasSize(1);
        assertThat(result.definedContact().getFirst()).isNull();
    }

    @Test
    void mapLogisticsLocation_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapLogisticsLocation(null)).isNull();
    }

    @Test
    void mapLogisticsLocation_shouldMapAllFieldsIncludingPostalAddress() {
        var addr = new TradeAddress("Port Road", null, "Dover", "CT17 9BU", "GB", null, null);
        var loc = new LogisticsLocation("GBDVR", "locUrl", "Dover", "PORT", addr);

        var result = mapper.mapLogisticsLocation(loc);

        assertThat(result.identifier()).isEqualTo("GBDVR");
        assertThat(result.urlId()).isEqualTo("locUrl");
        assertThat(result.name()).isEqualTo("Dover");
        assertThat(result.typeCode()).isEqualTo("PORT");
        assertThat(result.postalAddress().cityName()).isEqualTo("Dover");
    }

    @Test
    void mapLogisticsLocation_shouldHandleNullPostalAddress() {
        var loc = new LogisticsLocation("GBDVR", null, null, null, null);

        var result = mapper.mapLogisticsLocation(loc);

        assertThat(result.postalAddress()).isNull();
    }

    @Test
    void mapReferencedDocument_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapReferencedDocument(null)).isNull();
    }

    @Test
    void mapReferencedDocument_shouldMapThreeFields_andDropRelationshipTypeCode() {
        var doc = new ReferencedDocument("853", "ZZZ", "docref-1", "2026-05-05");

        var result = mapper.mapReferencedDocument(doc);

        assertThat(result.typeCode()).isEqualTo("853");
        assertThat(result.identifier()).isEqualTo("docref-1");
        assertThat(result.issueDateTime()).isEqualTo("2026-05-05");
    }
}
