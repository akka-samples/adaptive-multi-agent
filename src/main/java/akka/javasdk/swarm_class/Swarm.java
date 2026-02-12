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
 * <p>Two type parameters define the swarm's contract:
 * <ul>
 *   <li>{@code A} — the input type, passed to {@link #run(Object)} and available
 *       via {@link #getInput()} when configuring the swarm</li>
 *   <li>{@code B} — the result type, carried through to {@link SwarmResult.Completed#result()}</li>
 * </ul>
 *
 * <p>Users define a swarm by subclassing and overriding abstract methods to declare
 * instructions, handoffs, result type, etc. The agent loop and built-in operations
 * (pause, resume, stop) are provided by this base class using the Workflow machinery.
 *
 * @param <A> the input type accepted by {@link #run(Object)}
 * @param <B> the result type produced when the swarm completes successfully
 */
public abstract class Swarm<A, B>
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
  protected abstract Class<B> resultType();

  /** Maximum number of LLM round-trips before forced termination. Default: 10. */
  protected int maxTurns() {
    return 10;
  }

  /** Tools available to the orchestrator LLM. Default: none. */
  protected List<Object> tools() {
    return List.of();
  }

  // ========== Input accessor ==========

  /**
   * Access the input that was passed to {@link #run(Object)}.
   * Available for use in {@link #instructions()}, {@link #handoffs()},
   * and {@link #tools()} to dynamically configure the swarm based on input.
   */
  protected A getInput() {
    return null; // provided by runtime
  }

  // ========== Built-in command handlers ==========

  /** Start the swarm with the given input. */
  public Effect<Void> run(A input) {
    return null; // just dummy prototype
  }

  /** Get the current result/status as a fully-typed SwarmResult. */
  public ReadOnlyEffect<SwarmResult<B>> getResult() {
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
