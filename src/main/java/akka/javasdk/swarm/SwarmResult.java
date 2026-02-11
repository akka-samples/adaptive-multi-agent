/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.util.Optional;

/**
 * Result of a swarm execution, modeled as a sealed ADT. Each state carries only
 * the fields relevant to it — no optional fields needed.
 *
 * <p>Intended to be consumed via pattern matching:
 * <pre>{@code
 * switch (result) {
 *   case SwarmResult.Completed c -> c.resultAs(MyResult.class);
 *   case SwarmResult.Running r -> "turn " + r.currentTurn() + "/" + r.maxTurns();
 *   case SwarmResult.Paused p -> "paused: " + p.reason().message();
 *   case SwarmResult.Failed f -> "failed: " + f.reason();
 *   case SwarmResult.Stopped s -> "stopped: " + s.reason();
 * }
 * }</pre>
 */
public sealed interface SwarmResult {

  /**
   * The swarm is still executing.
   *
   * @param currentTurn the current turn number
   * @param maxTurns the maximum number of turns configured
   * @param currentAgent the agent currently being executed, if any
   * @param currentChildSwarm the child swarm currently executing, if any
   */
  record Running(
      int currentTurn,
      int maxTurns,
      Optional<String> currentAgent,
      Optional<String> currentChildSwarm) implements SwarmResult {}

  /**
   * The swarm is paused, awaiting external interaction (e.g. human approval).
   *
   * @param reason the reason for pausing
   * @param currentTurn the turn at which the swarm was paused
   * @param maxTurns the maximum number of turns configured
   */
  record Paused(
      PauseReason reason,
      int currentTurn,
      int maxTurns) implements SwarmResult {}

  /**
   * The swarm completed successfully.
   *
   * @param result the result object, as specified by {@link SwarmParams#resultAs()}
   */
  record Completed(Object result) implements SwarmResult {

    /**
     * Get the result cast to the expected type.
     *
     * @param <T> the expected result type
     * @param type the expected result class
     * @return the typed result
     * @throws ClassCastException if the result is not of the expected type
     */
    public <T> T resultAs(Class<T> type) {
      return type.cast(result);
    }
  }

  /**
   * The swarm failed.
   *
   * @param reason the failure reason
   */
  record Failed(String reason) implements SwarmResult {}

  /**
   * The swarm was explicitly stopped (terminal state, cannot resume).
   *
   * @param reason the reason it was stopped
   */
  record Stopped(String reason) implements SwarmResult {}
}
