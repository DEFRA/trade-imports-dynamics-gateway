package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// description, scientificName, commonName, typeCode, urlId omitted — always null in v0.1.0 (PR #52)
// applicableClassification is singular here — backend always produces exactly one (PR #52)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeLineItem(
    PimsApplicableClassification applicableClassification,
    List<PimsLineTradeDelivery> specifiedLineTradeDelivery,
    List<PimsLogisticsPackage> physicalReferencedLogisticsPackage,
    List<PimsTradeProductInstance> individualTradeProductInstance
) {}
