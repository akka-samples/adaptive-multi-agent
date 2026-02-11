/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.time.Instant;

/** Notification events emitted during swarm execution. */
public sealed interface SwarmEvent {

  record Started(String swarmId, Instant timestamp) implements SwarmEvent {}

  record AgentHandoff(String fromAgent, String toAgent, Instant timestamp) implements SwarmEvent {}

  record ToolCall(String agent, String toolName, Instant timestamp) implements SwarmEvent {}

  record TurnCompleted(int turn, int maxTurns, String activeAgent) implements SwarmEvent {}

  record Paused(PauseReason reason, Instant timestamp) implements SwarmEvent {}

  record Resumed(Instant timestamp) implements SwarmEvent {}

  record Completed(String resultJson, Instant timestamp) implements SwarmEvent {}

  record Failed(String reason, Instant timestamp) implements SwarmEvent {}
}
