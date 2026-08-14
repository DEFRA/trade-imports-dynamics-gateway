package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;

// systemName and className omitted — always null in v0.1.0 (PR #52)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsApplicableClassification(
    String systemId,
    PimsCodedValue classCode
) {}
