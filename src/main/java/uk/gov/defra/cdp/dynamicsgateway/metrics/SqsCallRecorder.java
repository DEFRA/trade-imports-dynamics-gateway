package uk.gov.defra.cdp.dynamicsgateway.metrics;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

/**
 * Records every call an SQS client makes as one {@code sqs} external call, its retries counted in
 * its duration, named after its SQS action in kebab case ({@code ReceiveMessage} as {@code
 * receive-message}), or {@code other} for an action outside the fixed set, and records its outcome
 * as the last SQS call outcome.
 */
@Component
@RequiredArgsConstructor
public class SqsCallRecorder implements ExecutionInterceptor {

  private static final ExecutionAttribute<Long> STARTED_AT_NANOS =
      new ExecutionAttribute<>("sqsCallStartedAtNanos");

  private final ExternalCallRecorder externalCallRecorder;

  @Override
  public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes attributes) {
    attributes.putAttribute(STARTED_AT_NANOS, externalCallRecorder.callStartedAt());
  }

  @Override
  public void afterExecution(Context.AfterExecution context, ExecutionAttributes attributes) {
    externalCallRecorder.recordSuccess(callOf(attributes), elapsed(attributes));
  }

  @Override
  public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes attributes) {
    externalCallRecorder.recordFailure(callOf(attributes), elapsed(attributes), context.exception());
  }

  static String operationOf(ExecutionAttributes attributes) {
    Set<String> operations =
        Set.of(
            "receive-message",
            "delete-message",
            "delete-message-batch",
            "change-message-visibility",
            "change-message-visibility-batch",
            "get-queue-attributes",
            "get-queue-url",
            "start-message-move-task",
            "purge-queue");
    String action = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    if (action == null) {
      return "other";
    }
    String operation = action.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    return operations.contains(operation) ? operation : "other";
  }

  private static ExternalCall callOf(ExecutionAttributes attributes) {
    return new ExternalCall(Dependency.SQS, operationOf(attributes));
  }

  private Duration elapsed(ExecutionAttributes attributes) {
    Long startedAt = attributes.getAttribute(STARTED_AT_NANOS);
    return startedAt == null ? Duration.ZERO : externalCallRecorder.elapsedSince(startedAt);
  }
}
