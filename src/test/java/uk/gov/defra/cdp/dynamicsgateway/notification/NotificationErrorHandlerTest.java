package uk.gov.defra.cdp.dynamicsgateway.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.amqp.exception.AmqpException;
import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.GenericMessage;
import uk.gov.defra.cdp.dynamicsgateway.exceptions.DynamicsGatewayException;

class NotificationErrorHandlerTest {

    private NotificationErrorHandler handler;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        handler = new NotificationErrorHandler(meterRegistry);
    }

    @Test
    void handle_shouldRethrow_whenServiceBusExceptionIsTransient() {
        AmqpException transientCause = new AmqpException(true, "timeout", null, null);
        ServiceBusException transientEx = new ServiceBusException(transientCause, ServiceBusErrorSource.SEND);

        assertThatThrownBy(() -> handler.handle(message(), wrap(transientEx)))
            .isInstanceOf(RuntimeException.class);
        assertThat(errorCount("retry")).isEqualTo(1.0);
    }

    @Test
    void handle_shouldReturn_whenServiceBusExceptionIsNotTransient() {
        AmqpException nonTransientCause = new AmqpException(false, "too large", null, null);
        ServiceBusException nonTransientEx = new ServiceBusException(nonTransientCause, ServiceBusErrorSource.SEND);

        assertThatCode(() -> handler.handle(message(), wrap(nonTransientEx)))
            .doesNotThrowAnyException();
        assertThat(errorCount("discarded")).isEqualTo(1.0);
    }

    @Test
    void handle_shouldRethrow_whenCauseIsIllegalStateException() {
        IllegalStateException illegalState = new IllegalStateException("sender disposed");

        assertThatThrownBy(() -> handler.handle(message(), wrap(illegalState)))
            .isInstanceOf(RuntimeException.class);
        assertThat(errorCount("retry")).isEqualTo(1.0);
    }

    @Test
    void handle_shouldReturn_whenCauseIsNullPointerException() {
        NullPointerException npe = new NullPointerException("null message");

        assertThatCode(() -> handler.handle(message(), wrap(npe)))
            .doesNotThrowAnyException();
        assertThat(errorCount("discarded")).isEqualTo(1.0);
    }

    @Test
    void handle_shouldReturn_whenCauseIsUnexpectedRuntimeException() {
        RuntimeException unexpected = new RuntimeException("something unexpected");

        assertThatCode(() -> handler.handle(message(), wrap(unexpected)))
            .doesNotThrowAnyException();
        assertThat(errorCount("discarded")).isEqualTo(1.0);
    }

    private GenericMessage<Object> message() {
        return new GenericMessage<>("test-body");
    }

    private DynamicsGatewayException wrap(Throwable cause) {
        return new DynamicsGatewayException("ASB error", cause);
    }

    private double errorCount(String action) {
        return meterRegistry.counter("notification.sqs.errors", "action", action).count();
    }
}
