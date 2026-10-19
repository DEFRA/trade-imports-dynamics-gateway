package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsExchangedDocument(
    String identifier,
    String traderAssignedId,
    String notificationStatusCode,
    Integer versionId,
    String issueDateTime,
    PimsAuthentication firstSignatoryAuthentication
) {}
