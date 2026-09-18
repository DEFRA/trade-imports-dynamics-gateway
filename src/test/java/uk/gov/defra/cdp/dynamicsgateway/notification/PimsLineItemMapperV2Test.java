package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.AnimalIdentifier;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ProductUnitQuantity;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeProductInstance;

class PimsLineItemMapperV2Test {

    private final PimsCommonMapperV2 commonMapper = new PimsCommonMapperV2();
    private final PimsLineItemMapperV2 mapper = new PimsLineItemMapperV2(commonMapper);

    @Test
    void mapConsignmentItem_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapConsignmentItem(null)).isNull();
    }

    @Test
    void mapTradeLineItem_shouldMapMultipleApplicableClassifications_notJustTheFirst() {
        // Given — the whole reason the v0.2.0 stack exists: no take-first behaviour
        var cn = new ApplicableClassification("CN", "Combined Nomenclature",
            new CodedValue("01020000", null, null), null);
        var speciesClass = new ApplicableClassification("SPECIES_CLASS", null,
            new CodedValue("MAMMAL", null, null), List.of("Bovidae"));
        var item = new TradeLineItem(List.of(cn, speciesClass), null, null, null, null, null, null, null, null);
        var ci = new ConsignmentItem(List.of(item));

        var result = mapper.mapConsignmentItem(ci);

        assertThat(result.includedTradeLineItem().getFirst().applicableClassification()).hasSize(2);
        assertThat(result.includedTradeLineItem().getFirst().applicableClassification().get(0).systemId())
            .isEqualTo("CN");
        assertThat(result.includedTradeLineItem().getFirst().applicableClassification().get(1).systemId())
            .isEqualTo("SPECIES_CLASS");
        assertThat(result.includedTradeLineItem().getFirst().applicableClassification().get(1).className())
            .containsExactly("Bovidae");
    }

    @Test
    void mapTradeLineItem_shouldMapDescriptionScientificNameAndCommonName() {
        var item = new TradeLineItem(null, List.of("Cattle"), "Bos taurus", "Cattle", null, null, null, null, null);
        var ci = new ConsignmentItem(List.of(item));

        var result = mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst();

        assertThat(result.description()).containsExactly("Cattle");
        assertThat(result.scientificName()).isEqualTo("Bos taurus");
        assertThat(result.commonName()).isEqualTo("Cattle");
    }

    @Test
    void mapApplicableClassification_shouldMapSystemNameAndClassName() {
        var ac = new ApplicableClassification("CN", "Combined Nomenclature",
            new CodedValue("01020000", "cnUrl", "Bovine"), List.of("Cattle"));
        var item = new TradeLineItem(List.of(ac), null, null, null, null, null, null, null, null);
        var ci = new ConsignmentItem(List.of(item));

        var result = mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .applicableClassification().getFirst();

        assertThat(result.systemName()).isEqualTo("Combined Nomenclature");
        assertThat(result.classCode().value()).isEqualTo("01020000");
        assertThat(result.className()).containsExactly("Cattle");
    }

    @Test
    void mapProductUnitQuantity_shouldMapUnitCode() {
        var qty = new ProductUnitQuantity(10, "H87");
        var delivery = new LineTradeDelivery(qty);
        var item = new TradeLineItem(null, null, null, null, null, null, List.of(delivery), null, null);
        var ci = new ConsignmentItem(List.of(item));

        var result = mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .specifiedLineTradeDelivery().getFirst().productUnitQuantity();

        assertThat(result.content()).isEqualTo(10);
        assertThat(result.unitCode()).isEqualTo("H87");
    }

    @Test
    void mapLogisticsPackage_shouldMapLevelCodeAndTypeCode() {
        var pkg = new LogisticsPackage(4, "CR", 2);
        var item = new TradeLineItem(null, null, null, null, null, null, null, List.of(pkg), null);
        var ci = new ConsignmentItem(List.of(item));

        var result = mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .physicalReferencedLogisticsPackage().getFirst();

        assertThat(result.levelCode()).isEqualTo(4);
        assertThat(result.typeCode()).isEqualTo("CR");
        assertThat(result.itemQuantity()).isEqualTo(2);
    }

    @Test
    void mapTradeProductInstance_shouldMapNameAndPermanentLocation() {
        var address = new TradeAddress("182 Ditchling Road", null, "Brighton", "BN1 7JE", "GB", null, null);
        var permanentLocation = new TradeParty(null, null, "Brighton home", null, null, address, null);
        var instance = new TradeProductInstance("Starlight", List.of(), permanentLocation);
        var item = new TradeLineItem(null, null, null, null, null, null, null, null, List.of(instance));
        var ci = new ConsignmentItem(List.of(item));

        var result = mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .individualTradeProductInstance().getFirst();

        assertThat(result.name()).isEqualTo("Starlight");
        assertThat(result.permanentLocation().name()).isEqualTo("Brighton home");
        assertThat(result.permanentLocation().postalAddress().cityName()).isEqualTo("Brighton");
    }

    @Test
    void mapTradeProductInstance_shouldHandleNullPermanentLocation() {
        var instance = new TradeProductInstance(null, List.of(), null);
        var item = new TradeLineItem(null, null, null, null, null, null, null, null, List.of(instance));
        var ci = new ConsignmentItem(List.of(item));

        assertThat(mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .individualTradeProductInstance().getFirst().permanentLocation()).isNull();
    }

    @Test
    void mapAnimalIdentifier_shouldMapUrlId() {
        var id = new AnimalIdentifier("EAR_TAG", "UK123456", "idUrl");
        var instance = new TradeProductInstance(null, List.of(id), null);
        var item = new TradeLineItem(null, null, null, null, null, null, null, null, List.of(instance));
        var ci = new ConsignmentItem(List.of(item));

        var result = mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .individualTradeProductInstance().getFirst().identifier().getFirst();

        assertThat(result.urlId()).isEqualTo("idUrl");
    }

    @Test
    void mapTradeLineItem_shouldHandleAllNullFields() {
        var item = new TradeLineItem(null, null, null, null, null, null, null, null, null);
        var ci = new ConsignmentItem(List.of(item));

        var result = mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst();

        assertThat(result.applicableClassification()).isEmpty();
        assertThat(result.description()).isNull();
        assertThat(result.specifiedLineTradeDelivery()).isEmpty();
        assertThat(result.physicalReferencedLogisticsPackage()).isEmpty();
        assertThat(result.individualTradeProductInstance()).isEmpty();
    }
}
