package uk.gov.defra.cdp.dynamicsgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

class SqsCallMetricsTest {

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "ReceiveMessage, receive-message",
    "DeleteMessageBatch, delete-message-batch",
    "ChangeMessageVisibilityBatch, change-message-visibility-batch",
    "GetQueueAttributes, get-queue-attributes",
    "StartMessageMoveTask, start-message-move-task",
    "PurgeQueue, purge-queue",
    "CreateQueue, other",
    "SendMessage, other"
  })
  void anSqsActionIsNamedInKebabCase_orOtherOutsideTheFixedSet(String action, String operation) {
    ExecutionAttributes attributes = new ExecutionAttributes();
    attributes.putAttribute(SdkExecutionAttribute.OPERATION_NAME, action);

    assertThat(SqsCallMetrics.operationOf(attributes)).isEqualTo(operation);
  }

  @Test
  void aCallWithNoActionIsOther() {
    assertThat(SqsCallMetrics.operationOf(new ExecutionAttributes())).isEqualTo("other");
  }
}
