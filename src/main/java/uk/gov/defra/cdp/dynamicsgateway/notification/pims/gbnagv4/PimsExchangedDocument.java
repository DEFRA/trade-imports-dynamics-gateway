package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv4;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv3.PimsAuthentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv3.PimsReferencedDocument;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsExchangedDocument(
    String identifier,
    String traderAssignedId,
    String notificationStatusCode,
    Integer versionId,
    String issueDateTime,
    PimsAuthentication firstSignatoryAuthentication,
    List<PimsReferencedDocument> referenceDocument
) {}
