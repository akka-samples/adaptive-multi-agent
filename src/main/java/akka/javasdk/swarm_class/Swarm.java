/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class;

import akka.NotUsed;
import akka.javasdk.swarm.SwarmEvent;
import akka.javasdk.workflow.Workflow;
import akka.stream.javadsl.Source;

import java.util.List;

/**
 * Alternative swarm design — a generic abstract class that extends Workflow.
 *
 * <p>The type parameter {@code R} is the result type. It flows through to
 * {@link #resultType()}, {@link #getResult()}, and {@link SwarmResult.Completed#result()},
 * giving fully-typed results without runtime casts at the call site.
 *
 * <p>Users define a swarm by subclassing and overriding abstract methods to declare
 * instructions, handoffs, result type, etc. The agent loop and built-in operations
 * (pause, resume, stop) are provided by this base class using the Workflow machinery.
 *
 * @param <R> the result type produced when the swarm completes successfully
 */
public abstract class Swarm<R>
    // Real implementation will probably not extend Workflow like this
    extends Workflow<SwarmState> {

  // ========== User defines the swarm by overriding these ==========

  /** The system instructions for the orchestrator LLM. */
  protected abstract String instructions();

  /** The handoff targets available to the orchestrator. */
  protected abstract List<Handoff> handoffs();

  /**
   * The expected result type. When the LLM produces output conforming to this type,
   * the swarm terminates successfully.
   */
  protected abstract Class<R> resultType();

  /** Maximum number of LLM round-trips before forced termination. Default: 10. */
  protected int maxTurns() {
    return 10;
  }

  /** Tools available to the orchestrator LLM. Default: none. */
  protected List<Object> tools() {
    return List.of();
  }

  // ========== Built-in command handlers ==========

  /** Start the swarm with a user message. */
  public Effect<Void> run(String userMessage) {
    return null; // just dummy prototype
  }

  /** Get the current result/status as a fully-typed SwarmResult. */
  public ReadOnlyEffect<SwarmResult<R>> getResult() {
    if (currentState() == null) {
      return effects().error("Swarm not started");
    }
    return effects().reply(currentState().toSwarmResult());
  }

  /** Resume a paused swarm with additional context (e.g. human approval). */
  public Effect<Void> resume(String message) {
    return null; // just dummy prototype
  }

  /** Stop the swarm permanently (terminal state). */
  public Effect<Void> stop(String reason) {
    return null; // just dummy prototype
  }

  /** Subscribe to real-time notification events from this swarm. */
  public Source<SwarmEvent, NotUsed> events() {
    return null; // just dummy prototype
  }

}
