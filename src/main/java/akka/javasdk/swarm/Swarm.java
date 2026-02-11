/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import akka.annotation.DoNotInherit;

/**
 * A built-in component that provides on-demand, multi-agent orchestration with automatic looping,
 * handoffs, and termination.
 *
 * <p>Unlike Agent/Workflow/Entity where users extend a base class, Swarm is invoked directly
 * with configuration via {@link SwarmParams}. The LLM is the orchestrator - it decides which
 * handoffs to invoke based on instructions, and the runtime manages the durable execution loop.
 *
 * <p>Not for user extension. Instance methods serve as method reference targets for the
 * component client pattern:
 * <pre>{@code
 * componentClient.forSwarm(swarmId)
 *     .method(Swarm::run)
 *     .invoke(params);
 * }</pre>
 */
@DoNotInherit
public abstract class Swarm {

  /**
   * Marker type for swarm effects, used as return type for method references
   * in the component client pattern.
   *
   * @param <T> the result type
   */
  @DoNotInherit
  public interface Effect<T> {}

  /** Start a swarm execution with the given parameters. */
  public abstract Effect<Void> run(SwarmParams params);

  /** Get the current result/status of this swarm. */
  public abstract Effect<SwarmResult> getResult();

  /** Pause a running swarm with the given reason. */
  public abstract Effect<Void> pause(String reason);

  /** Resume a paused swarm, optionally providing additional context. */
  public abstract Effect<Void> resume(String message);

  /** Stop a swarm permanently (terminal state, cannot resume). */
  public abstract Effect<Void> stop(String reason);
}
