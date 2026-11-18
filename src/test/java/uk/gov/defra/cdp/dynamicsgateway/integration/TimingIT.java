package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

  static Stream<Arguments> endpoints() {
    return Stream.of(
        endpoint(
            "POST /events",
            timing -> timing.postEvent(""),
            "controller.publishEvent.time"),
        endpoint(
            "GET /queue/notifications",
            timing -> timing.restTemplate.getForEntity("/queue/notifications", String.class),
            "controller.queueDepth.time",
            "service.queueDepth.time"),
        endpoint(
            "GET /dlq/notifications",
            timing -> timing.restTemplate.getForEntity("/dlq/notifications", String.class),
            "controller.dlqList.time",
            "service.dlqList.time"),
        endpoint(
            "POST /dlq/notifications/replay-all",
            timing ->
                timing.restTemplate.postForEntity(
                    "/dlq/notifications/replay-all", withAdminSecret(), String.class),
            "controller.dlqReplayAll.time",
            "service.dlqReplayAll.time"),
        endpoint(
            "POST /dlq/notifications/delete-all",
            timing ->
                timing.restTemplate.postForEntity(
                    "/dlq/notifications/delete-all", withAdminSecret(), String.class),
            "controller.dlqDeleteAll.time",
            "service.dlqDeleteAll.time"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("endpoints")
  void endpoint_shouldTimeOnlyItsControllerAndServiceCall(
      String request, Consumer<TimingIT> call, List<String> timed) {
    Map<String, Long> before = timerCounts();

    call.accept(this);

    assertOnlyTheseTimersMovedByOne(before, timed);
  }

  @Test
  void dlqDepthGauge_shouldTimeOnlyTheDlqDepthServiceCall() {
    Map<String, Long> before = timerCounts();

    meterRegistry.get("sqs.queue.depth").tag("queue", "notification-dlq").gauge().value();

    assertOnlyTheseTimersMovedByOne(before, List.of("service.dlqDepth.time"));
  }

  @Test
  void notificationDepthGauge_shouldTimeOnlyTheQueueDepthServiceCall() {
    Map<String, Long> before = timerCounts();

    meterRegistry.get("sqs.queue.depth").tag("queue", "notification").gauge().value();

    assertOnlyTheseTimersMovedByOne(before, List.of("service.queueDepth.time"));
  }

  private static Arguments endpoint(String request, Consumer<TimingIT> call, String... timed) {
    return Arguments.of(request, call, List.of(timed));
  }

  private void assertOnlyTheseTimersMovedByOne(Map<String, Long> before, List<String> moved) {
    Map<String, Long> actual = new TreeMap<>();
    timerCounts().forEach((name, count) -> actual.put(name, count - before.get(name)));
    Map<String, Long> expected = new TreeMap<>();
    before.keySet().forEach(name -> expected.put(name, 0L));
    moved.forEach(name -> expected.put(name, 1L));
    assertThat(actual).isEqualTo(expected);
  }

  private Map<String, Long> timerCounts() {
    Map<String, Long> counts = new TreeMap<>();
    for (String name : TIMERS) {
      counts.put(name, meterRegistry.find(name).timers().stream().mapToLong(Timer::count).sum());
    }
    return counts;
  }

  private static HttpEntity<Void> withAdminSecret() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Trade-Imports-Animals-Admin-Secret", ADMIN_SECRET);
    return new HttpEntity<>(headers);
  }
}
