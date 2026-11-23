package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEvent;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.OutboxEventMetadata;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Authentication;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.Clause;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ExchangedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.GbnAgData;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ReferencedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.PimsEventV4;
import uk.gov.defra.cdp.dynamicsgateway.notification.pims.gbnagv3.PimsReferencedDocument;

class PimsEventMapperV4Test {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");

    private final PimsEventMapperV4 mapper = PimsPayloadMappers.eventMapperV4();

    @Test
    void map_shouldStampV040SchemaVersionAndUriAndKeepTheIncomingCorrelationId() {
        OutboxEventMetadata metadata = new OutboxEventMetadata("corr-1", "1.0", "https://schema.url");
        OutboxEvent event = OutboxEventFixtures.builder().timestamp(NOW).metadata(metadata).build();

        PimsEventV4 result = mapper.map(event);

        assertThat(result.metadata().correlationId()).isEqualTo("corr-1");
        assertThat(result.metadata().schemaVersion()).isEqualTo("0.4.0");
        assertThat(result.metadata().schemaUri()).isEqualTo(
            "https://github.com/DEFRA/trade-imports-schemas/blob/main/schemas/profiles/imports/gb/pims/gbn-ag-pims-v0.4.0.schema.json");
    }

    @Test
    void map_shouldSendReferenceDocumentWithDocumentTypeListUrl() {
        ReferencedDocument refDoc = new ReferencedDocument(
            "GBN1", "https://refdata.tbc.defra.gov.uk/gbn-ag-document-types", "ZZZ", "GBHC1234567890", "2026-09-10");
        ExchangedDocument doc = new ExchangedDocument(
            "id-1", "trader-id", "SUBMITTED", 2, "2026-09-10", null, List.of(refDoc));

        var pimsDoc = mapper.map(eventWithData(new GbnAgData("m", "t", doc, null))).data().exchangedDocument();

        assertThat(pimsDoc.identifier()).isEqualTo("id-1");
        assertThat(pimsDoc.referenceDocument()).containsExactly(new PimsReferencedDocument(
            "GBN1", "https://refdata.tbc.defra.gov.uk/gbn-ag-document-types", "GBHC1234567890", "2026-09-10"));
    }

    @Test
    void map_shouldMapFirstSignatoryAuthenticationClauses() {
        Clause attestation = new Clause("II.1", "The animals described above are healthy.",
            "https://refdata.tbc.defra.gov.uk/clause/II.1");
        Clause transport = new Clause("II.2", "Transport conditions were met.", null);
        ExchangedDocument doc = new ExchangedDocument(
            "id-1", "trader-id", "SUBMITTED", 2, "2026-09-10",
            new Authentication(List.of(attestation, transport)), null);

        var pimsAuth = mapper.map(eventWithData(new GbnAgData("m", "t", doc, null)))
            .data().exchangedDocument().firstSignatoryAuthentication();

        assertThat(pimsAuth.includedClause()).hasSize(2);
        assertThat(pimsAuth.includedClause().getFirst().identifier()).isEqualTo("II.1");
        assertThat(pimsAuth.includedClause().getFirst().content())
            .isEqualTo("The animals described above are healthy.");
        assertThat(pimsAuth.includedClause().getFirst().urlId())
            .isEqualTo("https://refdata.tbc.defra.gov.uk/clause/II.1");
        assertThat(pimsAuth.includedClause().get(1).identifier()).isEqualTo("II.2");
        assertThat(pimsAuth.includedClause().get(1).urlId()).isNull();
    }

    @Test
    void map_shouldHandleNullFirstSignatoryAuthentication() {
        ExchangedDocument doc = new ExchangedDocument(
            "id-1", "trader-id", "SUBMITTED", 2, "2026-09-10", null, null);

        assertThat(mapper.map(eventWithData(new GbnAgData("m", "t", doc, null)))
            .data().exchangedDocument().firstSignatoryAuthentication()).isNull();
    }

    @Test
    void map_shouldHandleNullData() {
        assertThat(mapper.map(OutboxEventFixtures.builder().timestamp(NOW).build()).data()).isNull();
    }

    private OutboxEvent eventWithData(GbnAgData data) {
        return OutboxEventFixtures.builder().timestamp(NOW).data(data).build();
    }
}
