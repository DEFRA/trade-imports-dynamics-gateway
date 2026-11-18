package uk.gov.defra.cdp.dynamicsgateway.metrics;

/** Every system outside the INS boundary the service calls, each with its {@code dependency} tag. */
public enum Dependency {
  AZURE_SERVICE_BUS("azure-service-bus"),
  SQS("sqs");

  private final String tagValue;

  Dependency(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }
}
