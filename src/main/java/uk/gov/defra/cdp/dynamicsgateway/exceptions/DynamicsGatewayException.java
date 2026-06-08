package uk.gov.defra.cdp.dynamicsgateway.exceptions;

public class DynamicsGatewayException extends RuntimeException {

    public DynamicsGatewayException(String message) {
        super(message);
    }

    public DynamicsGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
