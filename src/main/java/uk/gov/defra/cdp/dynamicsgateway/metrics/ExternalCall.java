package uk.gov.defra.cdp.dynamicsgateway.metrics;

import java.util.Objects;

/**
 * One call to a system outside the INS boundary.
 *
 * @param dependency the system called
 * @param operation the operation on that system, for example {@code send-message}
 */
public record ExternalCall(Dependency dependency, String operation) {

  public static final ExternalCall SERVICE_BUS_SEND_MESSAGE =
      new ExternalCall(Dependency.AZURE_SERVICE_BUS, "send-message");

  public ExternalCall {
    Objects.requireNonNull(dependency, "dependency must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }
}
