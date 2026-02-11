/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.util.Optional;

/**
 * Status of a running or completed swarm execution.
 *
 * @param swarmId the unique identifier for this swarm instance
 * @param state current lifecycle state
 * @param currentTurn the current turn number (0-based)
 * @param maxTurns the maximum number of turns configured
 * @param currentAgent the agent currently being executed, if any
 * @param currentChildSwarm the child swarm currently executing, if any
 * @param pauseReason the reason for pausing, if the swarm is paused
 */
public record SwarmStatus(
    String swarmId,
    State state,
    int currentTurn,
    int maxTurns,
    Optional<String> currentAgent,
    Optional<String> currentChildSwarm,
    Optional<PauseReason> pauseReason) {

  public enum State {
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    STOPPED
  }
}
