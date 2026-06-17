package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.defra.cdp.dynamicsgateway.events.QueueMessageSender;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

@ExtendWith(MockitoExtension.class)
class NotificationSqsListenerTest {

    @Mock
    private QueueMessageSender queueMessageSender;

    private NotificationSqsListener listener;

    private static final String AGGREGATE_ID = "Imports.Notification.GBN-AG.GBN-AG-26-001";
    private static final String VALID_BODY =
        "{\"aggregateId\":\"" + AGGREGATE_ID + "\",\"eventType\":\"NotificationSubmitted\"}";

    @BeforeEach
    void setUp() {
        listener = new NotificationSqsListener(queueMessageSender, new ObjectMapper());
    }

    @Test
    void receive_shouldForwardToAsb_whenValid() {
        listener.receive(VALID_BODY, AGGREGATE_ID);

        verify(queueMessageSender).publish(any(), eq(AGGREGATE_ID));
    }

    @Test
    void receive_shouldReturn_whenAggregateIdIsNull() {
        listener.receive(VALID_BODY, null);

        verify(queueMessageSender, never()).publish(any(), any());
    }

    @Test
    void receive_shouldReturn_whenAggregateIdIsBlank() {
        listener.receive(VALID_BODY, "  ");

        verify(queueMessageSender, never()).publish(any(), any());
    }

    @Test
    void receive_shouldReturn_whenBodyIsInvalidJson() {
        listener.receive("not-json", AGGREGATE_ID);

        verify(queueMessageSender, never()).publish(any(), any());
    }

    @Test
    void receive_shouldThrow_whenAsbFails() {
        doThrow(new DynamicsGatewayException("ASB down"))
            .when(queueMessageSender).publish(any(), any());

        assertThatThrownBy(() -> listener.receive(VALID_BODY, AGGREGATE_ID))
            .isInstanceOf(DynamicsGatewayException.class);
    }
}
