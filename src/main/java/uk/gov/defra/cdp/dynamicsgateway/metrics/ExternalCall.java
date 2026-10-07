package uk.gov.defra.cdp.dynamicsgateway.metrics;

import java.util.Objects;

/**
 * One call to a system outside the INS boundary, named as the external-call metric contract names
 * it.
 *
 * @param dependency the system called, for example {@code azure-service-bus}
 * @param operation the operation on that system, for example {@code send-message}
 * @param interfaceId the volumetrics SYN interface id, or {@code null} where there is none
 */
public record ExternalCall(String dependency, String operation, String interfaceId) {

  public static final ExternalCall SERVICE_BUS_SEND_MESSAGE =
      new ExternalCall("azure-service-bus", "send-message", null);

  public ExternalCall {
    Objects.requireNonNull(dependency, "dependency must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }
}
