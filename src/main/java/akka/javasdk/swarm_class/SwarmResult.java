/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class;

import akka.javasdk.swarm.PauseReason;

import java.util.Optional;

/**
 * Typed result ADT for class-based swarms. Unlike {@link akka.javasdk.swarm.SwarmResult}
 * (which uses {@code resultAs(Class)} for type recovery), this version carries the result
 * type parameter {@code R} from the swarm definition through to the completed result.
 *
 * @param <R> the result type, matching the swarm's {@code resultType()}
 */
public sealed interface SwarmResult<R> {

  record Running<R>(int currentTurn, int maxTurns,
                    Optional<String> currentAgent,
                    Optional<String> currentChildSwarm) implements SwarmResult<R> {}

  record Paused<R>(PauseReason reason, int currentTurn, int maxTurns) implements SwarmResult<R> {}

  /** The swarm completed successfully with a fully-typed result. */
  record Completed<R>(R result) implements SwarmResult<R> {}

  record Failed<R>(String reason) implements SwarmResult<R> {}

  record Stopped<R>(String reason) implements SwarmResult<R> {}
}
