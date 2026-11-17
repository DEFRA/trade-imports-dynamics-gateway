package uk.gov.defra.cdp.dynamicsgateway.metrics;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Whether the most recent call to each dependency succeeded. The outcome has no time attached: one
 * failed call, on any of the dependency's operations, marks the whole dependency as failed until
 * its next call succeeds.
 */
@Component
public class LastCallOutcomes {

  private final Map<String, Boolean> succeededByDependency = new ConcurrentHashMap<>();

  public void recordSuccess(String dependency) {
    succeededByDependency.put(dependency, true);
  }

  public void recordFailure(String dependency) {
    succeededByDependency.put(dependency, false);
  }

  /**
   * The outcome of the dependency's most recent call.
   *
   * @param dependency the system called
   * @return whether it succeeded, or empty when the dependency has not been called since start-up
   */
  public Optional<Boolean> lastCallSucceeded(String dependency) {
    return Optional.ofNullable(succeededByDependency.get(dependency));
  }
}
