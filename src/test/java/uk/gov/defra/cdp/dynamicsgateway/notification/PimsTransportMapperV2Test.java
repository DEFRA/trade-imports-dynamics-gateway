package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsLocation;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMeans;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.ReferencedDocument;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TransportEvent;

class PimsTransportMapperV2Test {

    private final PimsCommonMapperV2 commonMapper = new PimsCommonMapperV2();
    private final PimsTransportMapperV2 mapper = new PimsTransportMapperV2(commonMapper);

    @Test
    void mapTransportMovement_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapTransportMovement(null)).isNull();
    }

    @Test
    void mapTransportMovement_shouldMapIdentifierAndUrlId() {
        var tm = new LogisticsTransportMovement("MV ATLANTIC STAR", "vesselUrl", 1, null, null, null);

        var result = mapper.mapTransportMovement(tm);

        assertThat(result.identifier()).isEqualTo("MV ATLANTIC STAR");
        assertThat(result.urlId()).isEqualTo("vesselUrl");
    }

    @Test
    void mapTransportMovement_shouldMapUsedLogisticsTransportMeans() {
        // Every other fixture in this class passes null for usedLogisticsTransportMeans, so the
        // non-null branch of mapTransportMeans was never exercised — the vessel name could have
        // been dropped or mis-wired without failing a test.
        var tm = new LogisticsTransportMovement(
            null, null, 1, new LogisticsTransportMeans("MV ATLANTIC STAR"), null, null);

        var result = mapper.mapTransportMovement(tm);

        assertThat(result.usedLogisticsTransportMeans()).isNotNull();
        assertThat(result.usedLogisticsTransportMeans().name()).isEqualTo("MV ATLANTIC STAR");
    }

    @Test
    void mapTransportMovement_shouldHandleNullUsedLogisticsTransportMeans() {
        var tm = new LogisticsTransportMovement(null, null, 1, null, null, null);

        assertThat(mapper.mapTransportMovement(tm).usedLogisticsTransportMeans()).isNull();
    }

    @Test
    void mapTransportMovement_shouldMapReferencedDocuments() {
        var doc = new ReferencedDocument("705", null, "BOL-2026-0042", null);
        var tm = new LogisticsTransportMovement(null, null, 1, null, List.of(doc), null);

        var result = mapper.mapTransportMovement(tm);

        assertThat(result.transportContractRelatedReferencedDocument()).hasSize(1);
        assertThat(result.transportContractRelatedReferencedDocument().getFirst().identifier())
            .isEqualTo("BOL-2026-0042");
    }

    @Test
    void mapTransportMovement_shouldHandleNullReferencedDocumentList() {
        var tm = new LogisticsTransportMovement(null, null, 1, null, null, null);

        assertThat(mapper.mapTransportMovement(tm).transportContractRelatedReferencedDocument()).isEmpty();
    }

    @Test
    void mapTransportMovement_shouldHandleNullElementInArrivalEventList() {
        var tm = new LogisticsTransportMovement(null, null, 1, null, null,
            Collections.singletonList(null));

        var result = mapper.mapTransportMovement(tm);

        assertThat(result.arrivalEvent()).hasSize(1);
        assertThat(result.arrivalEvent().getFirst()).isNull();
    }

    @Test
    void mapTransportMovement_shouldMapActualOccurrenceDateTimeAndOccurrenceLogisticsLocation() {
        Instant actual = Instant.parse("2026-08-15T02:30:00Z");
        var port = new LogisticsLocation("GBDVR", null, null, null, null);
        var event = new TransportEvent(null, actual, port);
        var tm = new LogisticsTransportMovement(null, null, 1, null, null, List.of(event));

        var result = mapper.mapTransportMovement(tm);

        assertThat(result.arrivalEvent().getFirst().actualOccurrenceDateTime()).isEqualTo(actual);
        assertThat(result.arrivalEvent().getFirst().occurrenceLogisticsLocation().identifier())
            .isEqualTo("GBDVR");
    }

    @Test
    void mapTransportMovement_shouldHandleNullOccurrenceLogisticsLocation() {
        var event = new TransportEvent(null, null, null);
        var tm = new LogisticsTransportMovement(null, null, 1, null, null, List.of(event));

        assertThat(mapper.mapTransportMovement(tm).arrivalEvent().getFirst()
            .occurrenceLogisticsLocation()).isNull();
    }
}
