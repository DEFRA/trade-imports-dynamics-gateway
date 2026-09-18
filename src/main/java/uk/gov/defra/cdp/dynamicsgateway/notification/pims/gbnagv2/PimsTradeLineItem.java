package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// typeCode and urlId deliberately excluded — resolved from CN-code reference data
// downstream rather than sourced from the notification (gap G18), per
// gbn-ag-pims-v0.2.0-changes.md.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeLineItem(
    List<PimsApplicableClassification> applicableClassification,
    List<String> description,
    String scientificName,
    String commonName,
    List<PimsLineTradeDelivery> specifiedLineTradeDelivery,
    List<PimsLogisticsPackage> physicalReferencedLogisticsPackage,
    List<PimsTradeProductInstance> individualTradeProductInstance
) {}
