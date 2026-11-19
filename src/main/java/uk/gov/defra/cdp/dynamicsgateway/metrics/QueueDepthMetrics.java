package uk.gov.defra.cdp.dynamicsgateway.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.defra.cdp.dynamicsgateway.notification.DlqService;
import uk.gov.defra.cdp.dynamicsgateway.notification.NotificationQueueService;

/**
 * Registers the gauge {@code sqs.queue.depth}, tagged {@code queue}: the approximate number of
 * messages waiting on the notification queue ({@code notification}) and on its dead-letter queue
 * ({@code notification-dlq}). Each reading asks SQS; a failed read is NaN.
 */
@Slf4j
@Component
public class QueueDepthMetrics {

  public QueueDepthMetrics(
      MeterRegistry meterRegistry,
      NotificationQueueService notificationQueueService,
      DlqService dlqService) {
    register(
        meterRegistry,
        "notification",
        () -> notificationQueueService.depth().approximateCount());
    register(meterRegistry, "notification-dlq", dlqService::dlqDepth);
  }

  private void register(MeterRegistry meterRegistry, String queue, LongSupplier readDepth) {
    Gauge.builder("sqs.queue.depth", readDepth, readQueueDepth -> depthOf(queue, readQueueDepth))
        .tag("queue", queue)
        .strongReference(true)
        .register(meterRegistry);
  }

  private double depthOf(String queue, LongSupplier readDepth) {
    try {
      return (double) readDepth.getAsLong();
    } catch (RuntimeException e) {
      log.warn("Could not read the depth of queue {}", queue, e);
      return Double.NaN;
    }
  }
}
