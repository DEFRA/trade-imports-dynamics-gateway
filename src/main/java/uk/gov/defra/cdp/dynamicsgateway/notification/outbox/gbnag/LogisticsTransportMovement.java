package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record LogisticsTransportMovement(
    String identifier,
    String urlId,
    Integer modeCode,
    LogisticsTransportMeans usedLogisticsTransportMeans,
    List<ReferencedDocument> transportContractRelatedReferencedDocument,
    List<TransportEvent> arrivalEvent
) {}
