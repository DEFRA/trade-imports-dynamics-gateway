package uk.gov.defra.cdp.dynamicsgateway.exceptions;

public class SqsNonRetryableException extends RuntimeException {

    public SqsNonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
