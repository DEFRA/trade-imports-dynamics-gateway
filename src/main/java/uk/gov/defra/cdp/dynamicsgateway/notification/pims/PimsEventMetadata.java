package uk.gov.defra.cdp.dynamicsgateway.notification.pims;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PimsEventMetadata(
    String correlationId,
    String schemaVersion,

    // Serialised as "schemaUri" to match event-envelope-v1.schema.json, which requires
    // ["schemaVersion", "schemaUri"] under additionalProperties:false — emitting "schemaUrl"
    // both omitted a required property and added a forbidden one, so no event validated
    // against the schema its own metadata points at (EUDPA-370 review, item 11). The Java
    // component keeps the schemaUrl name to match the generic-side OutboxEventMetadata;
    // only the wire name changes, and it changes for the v0.1.0 stream too.
    @JsonProperty("schemaUri")
    String schemaUrl
) {}
