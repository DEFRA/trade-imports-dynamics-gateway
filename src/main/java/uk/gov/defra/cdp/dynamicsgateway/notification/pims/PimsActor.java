package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsActor(
    String id,
    String source,
    String userType,
    String displayName,
    String organisationId,
    String onBehalfOfOrganisationId
) {}
