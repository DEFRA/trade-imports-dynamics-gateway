package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.LogisticsTransportMovement;
import uk.gov.defra.cdp.dynamicsgateway.notification.outbox.gbnag.TransportEvent;

class PimsTransportMapperTest {

    private final PimsTransportMapper mapper = new PimsTransportMapper();

    @Test
    void mapTransportMovement_shouldReturnNull_whenInputIsNull() {
        assertThat(mapper.mapTransportMovement(null)).isNull();
    }

    @Test
    void mapTransportMovement_shouldHandleNullMeansAndNullArrivalEventList() {
        // Given — null usedLogisticsTransportMeans exercises mapTransportMeans null branch;
        // null arrivalEvent list exercises mapList null branch
        var tm = new LogisticsTransportMovement(null, null, 1, null, null, null);

        // When
        var result = mapper.mapTransportMovement(tm);

        // Then
        assertThat(result.modeCode()).isEqualTo(1);
        assertThat(result.usedLogisticsTransportMeans()).isNull();
        assertThat(result.arrivalEvent()).isEmpty();
    }

    @Test
    void mapTransportMovement_shouldHandleNullElementInArrivalEventList() {
        // Given — null element in arrivalEvent list exercises mapTransportEvent null branch
        var tm = new LogisticsTransportMovement(null, null, 1, null, null,
            Collections.singletonList(null));

        // When
        var result = mapper.mapTransportMovement(tm);

        // Then — null event maps to null in output list
        assertThat(result.arrivalEvent()).hasSize(1);
        assertThat(result.arrivalEvent().getFirst()).isNull();
    }

    @Test
    void mapTransportMovement_shouldHandleNullScheduledDateTime() {
        // Given — TransportEvent with null scheduledOccurrenceDateTime
        var te = new TransportEvent(null, null, null);
        var tm = new LogisticsTransportMovement(null, null, 1, null, null, java.util.List.of(te));

        // When
        var result = mapper.mapTransportMovement(tm);

        // Then
        assertThat(result.arrivalEvent().getFirst().scheduledOccurrenceDateTime()).isNull();
    }
}
