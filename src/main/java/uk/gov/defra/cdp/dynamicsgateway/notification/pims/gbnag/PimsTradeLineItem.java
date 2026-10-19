package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeLineItem(
    PimsApplicableClassification applicableClassification,
    List<PimsLineTradeDelivery> specifiedLineTradeDelivery,
    List<PimsLogisticsPackage> physicalReferencedLogisticsPackage,
    List<PimsTradeProductInstance> individualTradeProductInstance
) {}
