package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// identifier, urlId, transportContractRelatedReferencedDocument omitted — always null in v0.1.0 (PR #52)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsLogisticsTransportMovement(
    Integer modeCode,
    PimsLogisticsTransportMeans usedLogisticsTransportMeans,
    List<PimsTransportEvent> arrivalEvent
) {}
