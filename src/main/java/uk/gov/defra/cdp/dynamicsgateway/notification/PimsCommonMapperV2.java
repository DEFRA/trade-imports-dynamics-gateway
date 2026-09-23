package uk.gov.defra.cdp.dynamicsgateway.notification;

import static uk.gov.defra.cdp.dynamicsgateway.notification.PimsMapperSupport.mapList;

import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.CodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.DefinedContact;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ReferencedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TradeParty;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsCodedValue;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsDefinedContact;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsLogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsReferencedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsTradeAddress;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2.PimsTradeParty;

/**
 * Mapping logic shared by two or more v0.2.0 mapper classes ({@link PimsConsignmentMapperV2},
 * {@link PimsTransportMapperV2}, {@link PimsLineItemMapperV2}, {@link PimsGbnAgDataMapperV2}) — kept
 * in one place rather than duplicated, and kept dependency-free so those classes can all depend on
 * it without risking a circular Spring bean dependency.
 */
@Component
class PimsCommonMapperV2 {

    PimsCodedValue mapCodedValue(CodedValue cv) {
        if (cv == null) return null;
        return new PimsCodedValue(cv.value(), cv.urlId(), cv.name());
    }

    PimsTradeParty mapTradeParty(TradeParty party) {
        if (party == null) return null;
        return new PimsTradeParty(
            party.identifier(),
            party.urlId(),
            party.name(),
            mapCodedValue(party.partyRoleCode()),
            mapList(party.partyTypeCode(), this::mapCodedValue),
            mapTradeAddress(party.postalAddress()),
            mapList(party.definedContact(), this::mapDefinedContact)
        );
    }

    PimsLogisticsLocation mapLogisticsLocation(LogisticsLocation loc) {
        if (loc == null) return null;
        return new PimsLogisticsLocation(
            loc.identifier(),
            loc.urlId(),
            loc.name(),
            loc.typeCode(),
            mapTradeAddress(loc.postalAddress())
        );
    }

    PimsReferencedDocument mapReferencedDocument(ReferencedDocument doc) {
        if (doc == null) return null;
        return new PimsReferencedDocument(doc.typeCode(), doc.identifier(), doc.issueDateTime());
    }

    private PimsTradeAddress mapTradeAddress(TradeAddress addr) {
        if (addr == null) return null;
        return new PimsTradeAddress(
            addr.lineOne(),
            addr.lineTwo(),
            addr.cityName(),
            addr.countryId(),
            addr.postcodeCode(),
            addr.countryName(),
            addr.countrySubDivisionName()
        );
    }

    private PimsDefinedContact mapDefinedContact(DefinedContact contact) {
        if (contact == null) return null;
        return new PimsDefinedContact(
            contact.personName(),
            contact.telephoneUniversalCommunication(),
            contact.emailURIUniversalCommunication()
        );
    }
}
