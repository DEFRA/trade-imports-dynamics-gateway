package uk.gov.defra.cdp.dynamicsgateway.notification;

import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.AnimalIdentifier;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ProductUnitQuantity;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeProductInstance;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsAnimalIdentifier;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsApplicableClassification;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsCodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsConsignmentItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLineTradeDelivery;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsLogisticsPackage;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsProductUnitQuantity;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeLineItem;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag.PimsTradeProductInstance;

@Component
class PimsLineItemMapper {

    PimsConsignmentItem mapConsignmentItem(ConsignmentItem item) {
        if (item == null) return null;
        return new PimsConsignmentItem(mapList(item.includedTradeLineItem(), this::mapTradeLineItem));
    }

    private PimsTradeLineItem mapTradeLineItem(TradeLineItem item) {
        if (item == null) return null;
        // applicableClassification: backend always produces exactly one — take first (PR #52)
        PimsApplicableClassification classification = item.applicableClassification() == null
            || item.applicableClassification().isEmpty()
            ? null
            : mapApplicableClassification(item.applicableClassification().getFirst());
        // description, scientificName, commonName, typeCode, urlId omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeLineItem(
            classification,
            mapList(item.specifiedLineTradeDelivery(), this::mapLineTradeDelivery),
            mapList(item.physicalReferencedLogisticsPackage(), this::mapLogisticsPackage),
            mapList(item.individualTradeProductInstance(), this::mapTradeProductInstance)
        );
    }

    private PimsApplicableClassification mapApplicableClassification(ApplicableClassification ac) {
        if (ac == null) return null;
        // systemName and className omitted — always null in v0.1.0 (PR #52)
        PimsCodedValue classCode = ac.classCode() == null ? null
            : new PimsCodedValue(ac.classCode().value(), ac.classCode().urlId());
        return new PimsApplicableClassification(ac.systemId(), classCode);
    }

    private PimsLineTradeDelivery mapLineTradeDelivery(LineTradeDelivery delivery) {
        if (delivery == null) return null;
        return new PimsLineTradeDelivery(mapProductUnitQuantity(delivery.productUnitQuantity()));
    }

    private PimsProductUnitQuantity mapProductUnitQuantity(ProductUnitQuantity qty) {
        if (qty == null) return null;
        // unitCode omitted — always null in v0.1.0 (PR #52)
        return new PimsProductUnitQuantity(qty.content());
    }

    private PimsLogisticsPackage mapLogisticsPackage(LogisticsPackage pkg) {
        if (pkg == null) return null;
        // levelCode and typeCode omitted — always null in v0.1.0 (PR #52)
        return new PimsLogisticsPackage(pkg.itemQuantity());
    }

    private PimsTradeProductInstance mapTradeProductInstance(TradeProductInstance instance) {
        if (instance == null) return null;
        // name and permanentLocation omitted — always null in v0.1.0 (PR #52)
        return new PimsTradeProductInstance(mapList(instance.identifier(), this::mapAnimalIdentifier));
    }

    private PimsAnimalIdentifier mapAnimalIdentifier(AnimalIdentifier id) {
        if (id == null) return null;
        // urlId omitted — always null in v0.1.0 (PR #52)
        return new PimsAnimalIdentifier(id.typeCode(), id.content());
    }

    private <A, B> List<B> mapList(List<A> list, Function<A, B> fn) {
        if (list == null) return List.of();
        return list.stream().map(fn).toList();
    }
}
