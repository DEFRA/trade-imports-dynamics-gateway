package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv2;

import com.fasterxml.jackson.annotation.JsonInclude;

// Deliberately 3 fields, not the generic model's 4 — relationshipTypeCode excluded,
// per gbn-ag-pims-v0.2.0-changes.md ("Accompanying Document" fields are the only ones confirmed).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsReferencedDocument(
    String typeCode,
    String identifier,
    String issueDateTime
) {}
