package uk.gov.defra.cdp.dynamicsgateway.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DynamicsGatewayExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        DynamicsGatewayException ex = new DynamicsGatewayException("error message");

        assertThat(ex.getMessage()).isEqualTo("error message");
    }

    @Test
    void shouldCreateExceptionWithMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        DynamicsGatewayException ex = new DynamicsGatewayException("error message", cause);

        assertThat(ex.getMessage()).isEqualTo("error message");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}