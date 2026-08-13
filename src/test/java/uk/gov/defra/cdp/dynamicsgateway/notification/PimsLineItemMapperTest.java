package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.AnimalIdentifier;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeProductInstance;

class PimsLineItemMapperTest {

    private final PimsLineItemMapper mapper = new PimsLineItemMapper();

    @Test
    void mapConsignmentItem_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapConsignmentItem(null)).isNull();
    }

    @Test
    void mapConsignmentItem_shouldHandleNullTradeLineItemElement() {
        // Given — null element in includedTradeLineItem exercises mapTradeLineItem null branch
        var ci = new ConsignmentItem(Collections.singletonList(null));

        // When
        var result = mapper.mapConsignmentItem(ci);

        // Then — null item maps to null in output list
        assertThat(result.includedTradeLineItem()).hasSize(1);
        assertThat(result.includedTradeLineItem().getFirst()).isNull();
    }

    @Test
    void mapConsignmentItem_shouldHandleNullClassCodeInApplicableClassification() {
        // Given — ApplicableClassification with null classCode exercises the classCode null guard
        var ac = new ApplicableClassification("HS", null, null, null);
        var item = new TradeLineItem(List.of(ac), null, null, null, null, null, null, null, null);
        var ci = new ConsignmentItem(List.of(item));

        // When
        var result = mapper.mapConsignmentItem(ci);

        // Then
        assertThat(result.includedTradeLineItem().getFirst().applicableClassification().systemId()).isEqualTo("HS");
        assertThat(result.includedTradeLineItem().getFirst().applicableClassification().classCode()).isNull();
    }

    @Test
    void mapConsignmentItem_shouldHandleNullLineTradeDeliveryElement() {
        // Given — null element in specifiedLineTradeDelivery exercises mapLineTradeDelivery null branch
        var item = new TradeLineItem(null, null, null, null, null, null,
            Collections.singletonList(null), null, null);
        var ci = new ConsignmentItem(List.of(item));

        // When / Then
        assertThat(mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .specifiedLineTradeDelivery().getFirst()).isNull();
    }

    @Test
    void mapConsignmentItem_shouldHandleNullProductUnitQuantity() {
        // Given — LineTradeDelivery with null productUnitQuantity exercises mapProductUnitQuantity null branch
        var delivery = new LineTradeDelivery(null);
        var item = new TradeLineItem(null, null, null, null, null, null, List.of(delivery), null, null);
        var ci = new ConsignmentItem(List.of(item));

        // When / Then
        assertThat(mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .specifiedLineTradeDelivery().getFirst().productUnitQuantity()).isNull();
    }

    @Test
    void mapConsignmentItem_shouldHandleNullLogisticsPackageElement() {
        // Given — null element in physicalReferencedLogisticsPackage exercises mapLogisticsPackage null branch
        var item = new TradeLineItem(null, null, null, null, null, null, null,
            Collections.singletonList(null), null);
        var ci = new ConsignmentItem(List.of(item));

        // When / Then
        assertThat(mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .physicalReferencedLogisticsPackage().getFirst()).isNull();
    }

    @Test
    void mapConsignmentItem_shouldHandleNullTradeProductInstanceElement() {
        // Given — null element in individualTradeProductInstance exercises mapTradeProductInstance null branch
        var item = new TradeLineItem(null, null, null, null, null, null, null, null,
            Collections.singletonList(null));
        var ci = new ConsignmentItem(List.of(item));

        // When / Then
        assertThat(mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .individualTradeProductInstance().getFirst()).isNull();
    }

    @Test
    void mapConsignmentItem_shouldHandleNullAnimalIdentifierElement() {
        // Given — null element in identifier list exercises mapAnimalIdentifier null branch
        var instance = new TradeProductInstance(null, Collections.singletonList(null), null);
        var item = new TradeLineItem(null, null, null, null, null, null, null, null, List.of(instance));
        var ci = new ConsignmentItem(List.of(item));

        // When / Then
        assertThat(mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .individualTradeProductInstance().getFirst().identifier().getFirst()).isNull();
    }

    @Test
    void mapConsignmentItem_shouldHandleNullLogisticsPackage() {
        // Given — LogisticsPackage with null fields — exercises mapLogisticsPackage non-null path
        var pkg = new LogisticsPackage(null, null, 3);
        var item = new TradeLineItem(null, null, null, null, null, null, null, List.of(pkg), null);
        var ci = new ConsignmentItem(List.of(item));

        // When / Then
        assertThat(mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .physicalReferencedLogisticsPackage().getFirst().itemQuantity()).isEqualTo(3);
    }

    @Test
    void mapConsignmentItem_shouldHandleNullAnimalIdentifier() {
        // Given — AnimalIdentifier with values exercises the mapping
        var id = new AnimalIdentifier("EAR_TAG", "UK999", "dropped-url");
        var instance = new TradeProductInstance(null, List.of(id), null);
        var item = new TradeLineItem(null, null, null, null, null, null, null, null, List.of(instance));
        var ci = new ConsignmentItem(List.of(item));

        // When / Then
        assertThat(mapper.mapConsignmentItem(ci).includedTradeLineItem().getFirst()
            .individualTradeProductInstance().getFirst().identifier().getFirst().typeCode()).isEqualTo("EAR_TAG");
    }
}
