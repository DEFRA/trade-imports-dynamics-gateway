package uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnag;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsApplicableClassification(
    String systemId,
    PimsCodedValue classCode
) {}
