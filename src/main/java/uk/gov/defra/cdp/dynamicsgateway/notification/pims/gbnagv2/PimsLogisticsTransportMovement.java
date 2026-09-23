package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsLogisticsTransportMovement(
    String identifier,
    String urlId,
    Integer modeCode,
    PimsLogisticsTransportMeans usedLogisticsTransportMeans,
    List<PimsReferencedDocument> transportContractRelatedReferencedDocument,
    List<PimsTransportEvent> arrivalEvent
) {}
