package uk.gov.defra.cdp.dynamicsgateway.exceptions;

public class SqsRetryableException extends RuntimeException {

    public SqsRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
