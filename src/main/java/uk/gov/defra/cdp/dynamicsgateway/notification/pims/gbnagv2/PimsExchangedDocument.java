package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// issuer deliberately excluded — deferred to its own follow-up ticket (Responsible Person
// for Load / Contact Address), see gbn-ag-pims-v0.2.0-changes.md.
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
