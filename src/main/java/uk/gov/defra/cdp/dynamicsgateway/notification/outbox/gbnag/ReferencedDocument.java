package uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ReferencedDocument(
    String typeCode,
    String relationshipTypeCode,
    String identifier,
    String issueDateTime
) {}
