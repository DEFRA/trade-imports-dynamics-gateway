package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// partyRoleCode and definedContact omitted — always null in v0.1.0 (PR #52)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeParty(
    String identifier,
    String urlId,
    String name,
    List<PimsCodedValue> partyTypeCode,
    PimsTradeAddress postalAddress
) {}
