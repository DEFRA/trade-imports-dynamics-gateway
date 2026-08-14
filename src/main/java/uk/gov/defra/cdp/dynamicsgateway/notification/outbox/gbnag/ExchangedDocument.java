package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ExchangedDocument(
    String identifier,
    String traderAssignedId,
    String notificationStatusCode,
    Integer versionId,
    String issueDateTime,
    TradeParty issuer,
    Authentication firstSignatoryAuthentication,
    List<ReferencedDocument> referenceDocument
) {}
