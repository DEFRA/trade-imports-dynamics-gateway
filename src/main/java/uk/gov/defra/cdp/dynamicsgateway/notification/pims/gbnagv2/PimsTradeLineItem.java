package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// typeCode and urlId deliberately excluded — resolved from CN-code reference data
// downstream rather than sourced from the notification (gap G18), per
// gbn-ag-pims-v0.2.0-changes.md.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeLineItem(
    // NON_EMPTY, not NON_NULL: the schema sets minItems:1 on this field, and mapList yields
    // an empty list (never null) when the upstream list is absent. NON_NULL would leave "[]"
    // on the wire, which the schema rejects; tradeLineItem has no required list, so omitting
    // the field entirely is valid (EUDPA-370 review, item 14).
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<PimsApplicableClassification> applicableClassification,
    List<String> description,
    String scientificName,
    String commonName,
    List<PimsLineTradeDelivery> specifiedLineTradeDelivery,
    List<PimsLogisticsPackage> physicalReferencedLogisticsPackage,
    List<PimsTradeProductInstance> individualTradeProductInstance
) {}
