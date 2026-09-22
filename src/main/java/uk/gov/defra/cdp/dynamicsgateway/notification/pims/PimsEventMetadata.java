package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsEventMetadata(
    String correlationId,
    String schemaVersion,

    // Serialised as "schemaUri" to match event-envelope-v1.schema.json, which requires
    // ["schemaVersion", "schemaUri"] under additionalProperties:false. Emitting "schemaUrl"
    // both omitted a required property and added a forbidden one, so no event validated
    // against the schema its own metadata points at (EUDPA-370 review, item 11). The Java
    // component name is left alone so it still matches the generic side, and only the wire
    // name changes — which also changes the v0.1.0 payload, not just v0.2.0.
    @JsonProperty("schemaUri")
    String schemaUrl
) {}
