package uk.gov.defra.cdp.dynamicsgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

class SqsCallRecorderTest {

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "ReceiveMessage, receive-message",
    "DeleteMessage, delete-message",
    "DeleteMessageBatch, delete-message-batch",
    "ChangeMessageVisibility, change-message-visibility",
    "ChangeMessageVisibilityBatch, change-message-visibility-batch",
    "GetQueueAttributes, get-queue-attributes",
    "GetQueueUrl, get-queue-url",
    "StartMessageMoveTask, start-message-move-task",
    "PurgeQueue, purge-queue",
    "CreateQueue, other",
    "SendMessage, other"
  })
  void operationOf_shouldBeTheActionInKebabCase_orOtherOutsideTheFixedSet(
      String action, String operation) {
    ExecutionAttributes attributes = new ExecutionAttributes();
    attributes.putAttribute(SdkExecutionAttribute.OPERATION_NAME, action);

    assertThat(SqsCallRecorder.operationOf(attributes)).isEqualTo(operation);
  }

  @Test
  void operationOf_shouldBeOther_whenTheCallHasNoAction() {
    assertThat(SqsCallRecorder.operationOf(new ExecutionAttributes())).isEqualTo("other");
  }
}
