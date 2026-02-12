/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class2;

import akka.NotUsed;
import akka.javasdk.swarm.SwarmEvent;
import akka.javasdk.swarm_class.SwarmParams;
import akka.javasdk.swarm_class.SwarmResult;
import akka.javasdk.swarm_class.SwarmState;
import akka.javasdk.workflow.Workflow;
import akka.stream.javadsl.Source;

/**
 * Class-based swarm with typed input and result.
 *
 * <p>Two type parameters define the swarm's contract:
 * <ul>
 *   <li>{@code A} — the input type, passed to {@link #run(Object)} and available
 *       via {@link #getInput()} when configuring the swarm in {@link #parameters()}</li>
 *   <li>{@code B} — the result type, carried through to {@link SwarmResult.Completed#result()}</li>
 * </ul>
 *
 * <p>The single {@link #parameters()} method returns a {@link SwarmParams} that configures
 * instructions, handoffs, and tools. It can use {@link #getInput()} to dynamically
 * choose different configurations based on the input content.
 *
 * @param <A> the input type accepted by {@link #run(Object)}
 * @param <B> the result type produced when the swarm completes successfully
 */
public abstract class Swarm<A, B>
    // Real implementation will probably not extend Workflow like this
    extends Workflow<SwarmState> {

  // ========== User defines the swarm by overriding these ==========

  /**
   * Configure the swarm. Called when {@link #run(Object)} is invoked.
   *
   * <p>Use {@link #getInput()} to access the input and dynamically choose
   * instructions, handoffs, and tools based on the input content.
   *
   * @return the swarm configuration to use for this execution
   */
  protected abstract SwarmParams parameters();

  /**
   * The expected result type. When the LLM produces output conforming to this type,
   * the swarm terminates successfully.
   */
  protected abstract Class<B> resultType();

  // ========== Input accessor ==========

  /**
   * Access the input that was passed to {@link #run(Object)}.
   * Available for use in {@link #parameters()} to dynamically configure the swarm.
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
