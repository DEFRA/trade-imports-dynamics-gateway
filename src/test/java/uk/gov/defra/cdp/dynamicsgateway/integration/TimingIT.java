package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "admin.secret=" + TimingIT.ADMIN_SECRET)
class TimingIT extends IntegrationBase {

  static final String ADMIN_SECRET = "timing-secret";

  private static final List<String> TIMERS =
      List.of(
          "controller.publishEvent.time",
          "controller.queueDepth.time",
          "controller.dlqList.time",
          "controller.dlqReplayAll.time",
          "controller.dlqDeleteAll.time",
          "service.queueDepth.time",
          "service.dlqList.time",
          "service.dlqReplayAll.time",
          "service.dlqDeleteAll.time",
          "service.dlqDepth.time");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void eachEndpointAddsOneCallToOnlyItsOwnControllerAndServiceTimers() {
    Map<String, Long> beforePublish = timerCounts();
    restTemplate.postForEntity("/events", json("{\"aggregateId\":\"\"}"), String.class);
    assertOnlyTheseTimersMovedByOne(beforePublish, "controller.publishEvent.time");

    Map<String, Long> beforeQueueDepth = timerCounts();
    restTemplate.getForEntity("/queue/notifications", String.class);
    assertOnlyTheseTimersMovedByOne(
        beforeQueueDepth, "controller.queueDepth.time", "service.queueDepth.time");

    Map<String, Long> beforeDlqList = timerCounts();
    restTemplate.getForEntity("/dlq/notifications", String.class);
    assertOnlyTheseTimersMovedByOne(beforeDlqList, "controller.dlqList.time", "service.dlqList.time");

    Map<String, Long> beforeReplayAll = timerCounts();
    restTemplate.postForEntity("/dlq/notifications/replay-all", withAdminSecret(), String.class);
    assertOnlyTheseTimersMovedByOne(
        beforeReplayAll, "controller.dlqReplayAll.time", "service.dlqReplayAll.time");

    Map<String, Long> beforeDeleteAll = timerCounts();
    restTemplate.postForEntity("/dlq/notifications/delete-all", withAdminSecret(), String.class);
    assertOnlyTheseTimersMovedByOne(
        beforeDeleteAll, "controller.dlqDeleteAll.time", "service.dlqDeleteAll.time");
  }

  @Test
  void readingTheDlqDepthGaugeAddsOneCallToOnlyTheDlqDepthServiceTimer() {
    Map<String, Long> before = timerCounts();

    meterRegistry.get("sqs.queue.depth").tag("queue", "notification-dlq").gauge().value();

    assertOnlyTheseTimersMovedByOne(before, "service.dlqDepth.time");
  }

  private void assertOnlyTheseTimersMovedByOne(Map<String, Long> before, String... moved) {
    Map<String, Long> actual = new TreeMap<>();
    timerCounts().forEach((name, count) -> actual.put(name, count - before.get(name)));
    Map<String, Long> expected = new TreeMap<>();
    before.keySet().forEach(name -> expected.put(name, 0L));
    for (String name : moved) {
      expected.put(name, 1L);
    }
    assertThat(actual).isEqualTo(expected);
  }

  private Map<String, Long> timerCounts() {
    Map<String, Long> counts = new TreeMap<>();
    for (String name : TIMERS) {
      counts.put(name, meterRegistry.find(name).timers().stream().mapToLong(Timer::count).sum());
    }
    return counts;
  }

  private static HttpEntity<String> json(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private static HttpEntity<Void> withAdminSecret() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Trade-Imports-Animals-Admin-Secret", ADMIN_SECRET);
    return new HttpEntity<>(headers);
  }
}
