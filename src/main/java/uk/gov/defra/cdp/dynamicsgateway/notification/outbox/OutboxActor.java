package uk.gov.defra.cdp.dynamicsgateway.notification.outbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record OutboxActor(
    String id,
    String source,
    String userType,
    String displayName,
    String organisationId,
    String onBehalfOfOrganisationId
) {}
