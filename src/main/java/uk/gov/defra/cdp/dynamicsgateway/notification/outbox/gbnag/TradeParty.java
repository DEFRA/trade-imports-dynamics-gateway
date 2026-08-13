package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record TradeParty(
    String identifier,
    String urlId,
    String name,
    CodedValue partyRoleCode,
    List<CodedValue> partyTypeCode,
    TradeAddress postalAddress,
    List<DefinedContact> definedContact
) {}
