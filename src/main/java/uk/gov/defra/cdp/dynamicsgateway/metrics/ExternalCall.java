package uk.gov.defra.cdp.dynamicsgateway.metrics;

import java.util.List;
import java.util.Objects;

/**
 * One call to a system outside the INS boundary.
 *
 * @param dependency the system called, for example {@code azure-service-bus}
 * @param operation the operation on that system, for example {@code send-message}
 */
public record ExternalCall(String dependency, String operation) {

  public static final String AZURE_SERVICE_BUS = "azure-service-bus";
  public static final String SQS = "sqs";

  public static final ExternalCall SERVICE_BUS_SEND_MESSAGE =
      new ExternalCall(AZURE_SERVICE_BUS, "send-message");

  /** Every dependency the service calls, each once. */
  public static final List<String> DEPENDENCIES = List.of(AZURE_SERVICE_BUS, SQS);

  public ExternalCall {
    Objects.requireNonNull(dependency, "dependency must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }
}
