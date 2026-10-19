package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsTradeLineItem(
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<PimsApplicableClassification> applicableClassification,
    List<String> description,
    String scientificName,
    String commonName,
    List<PimsLineTradeDelivery> specifiedLineTradeDelivery,
    List<PimsLogisticsPackage> physicalReferencedLogisticsPackage,
    List<PimsTradeProductInstance> individualTradeProductInstance
) {}
