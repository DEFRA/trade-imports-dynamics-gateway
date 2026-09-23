package uk.gov.defra.cdp.dynamicsgateway.notification;

import static uk.gov.defra.cdp.dynamicsgateway.notification.PimsMapperSupport.mapList;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.AnimalIdentifier;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ProductUnitQuantity;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeProductInstance;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsAnimalIdentifier;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsLineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsLogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsProductUnitQuantity;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsTradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsTradeProductInstance;

@Component
@RequiredArgsConstructor
class PimsLineItemMapperV2 {

    private final PimsCommonMapperV2 commonMapper;

    PimsConsignmentItem mapConsignmentItem(ConsignmentItem item) {
        if (item == null) return null;
        return new PimsConsignmentItem(mapList(item.includedTradeLineItem(), this::mapTradeLineItem));
    }

    private PimsTradeLineItem mapTradeLineItem(TradeLineItem item) {
        if (item == null) return null;
        return new PimsTradeLineItem(
            mapList(item.applicableClassification(), this::mapApplicableClassification),
            item.description(),
            item.scientificName(),
            item.commonName(),
            mapList(item.specifiedLineTradeDelivery(), this::mapLineTradeDelivery),
            mapList(item.physicalReferencedLogisticsPackage(), this::mapLogisticsPackage),
            mapList(item.individualTradeProductInstance(), this::mapTradeProductInstance)
        );
    }

    private PimsApplicableClassification mapApplicableClassification(ApplicableClassification ac) {
        if (ac == null) return null;
        return new PimsApplicableClassification(
            ac.systemId(),
            ac.systemName(),
            commonMapper.mapCodedValue(ac.classCode()),
            ac.className()
        );
    }

    private PimsLineTradeDelivery mapLineTradeDelivery(LineTradeDelivery delivery) {
        if (delivery == null) return null;
        return new PimsLineTradeDelivery(mapProductUnitQuantity(delivery.productUnitQuantity()));
    }

    private PimsProductUnitQuantity mapProductUnitQuantity(ProductUnitQuantity qty) {
        if (qty == null) return null;
        return new PimsProductUnitQuantity(qty.content(), qty.unitCode());
    }

    private PimsLogisticsPackage mapLogisticsPackage(LogisticsPackage pkg) {
        if (pkg == null) return null;
        return new PimsLogisticsPackage(pkg.levelCode(), pkg.typeCode(), pkg.itemQuantity());
    }

    private PimsTradeProductInstance mapTradeProductInstance(TradeProductInstance instance) {
        if (instance == null) return null;
        return new PimsTradeProductInstance(
            instance.name(),
            mapList(instance.identifier(), this::mapAnimalIdentifier),
            commonMapper.mapTradeParty(instance.permanentLocation())
        );
    }

    private PimsAnimalIdentifier mapAnimalIdentifier(AnimalIdentifier id) {
        if (id == null) return null;
        return new PimsAnimalIdentifier(id.typeCode(), id.content(), id.urlId());
    }
}
