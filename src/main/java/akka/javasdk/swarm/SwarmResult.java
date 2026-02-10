/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.util.Optional;

/**
 * Result wrapper for a swarm execution, combining status with the result.
 *
 * @param status current swarm status
 * @param result the result object, present when the swarm has completed successfully
 * @param failureReason the failure reason, present when the swarm has failed
 */
public record SwarmResult(
    SwarmStatus status,
    Optional<Object> result,
    Optional<String> failureReason) {

  public boolean isCompleted() {
    return status.state() == SwarmStatus.State.COMPLETED;
  }

  public boolean isFailed() {
    return status.state() == SwarmStatus.State.FAILED;
  }

  public boolean isRunning() {
    return status.state() == SwarmStatus.State.RUNNING;
  }

  public boolean isPaused() {
    return status.state() == SwarmStatus.State.PAUSED;
  }

  /**
   * Get the result cast to the expected type, as specified by {@link SwarmParams#responseAs()}.
   *
   * @param <T> the expected result type
   * @param type the expected result class
   * @return the typed result
   * @throws IllegalStateException if the swarm has not completed
   * @throws ClassCastException if the result is not of the expected type
   */
  @SuppressWarnings("unchecked")
  public <T> T resultAs(Class<T> type) {
    if (!isCompleted()) {
      throw new IllegalStateException("Swarm has not completed, current state: " + status.state());
    }
    return type.cast(result.orElseThrow(() ->
        new IllegalStateException("Swarm completed but no result available")));
  }
}
