/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class2;

import akka.NotUsed;
import akka.javasdk.swarm.SwarmEvent;
import akka.javasdk.swarm_class.SwarmResult;
import akka.javasdk.swarm_class.SwarmState;
import akka.javasdk.workflow.Workflow;
import akka.stream.javadsl.Source;

/**
 * Alternative class-based swarm design with a single {@link #parameters(String)} method.
 *
 * <p>Instead of separate abstract methods for instructions, handoffs, and tools,
 * this design uses one method that receives the user input and returns a {@link SwarmParams}.
 * This allows the swarm to dynamically configure itself based on the input — e.g.,
 * choosing different handoffs or instructions depending on what the user asked for.
 *
 * <p>The result type {@code R} is still a class-level type parameter with a separate
 * {@link #resultType()} method, since it defines the swarm's contract and doesn't
 * change per invocation.
 *
 * @param <R> the result type produced when the swarm completes successfully
 */
public abstract class Swarm<R>
    // Real implementation will probably not extend Workflow like this
    extends Workflow<SwarmState> {

  // ========== User defines the swarm by overriding these ==========

  /**
   * Configure the swarm for a given user input. Called when {@link #run(String)} is invoked.
   *
   * <p>The user message is passed in so the swarm can dynamically choose instructions,
   * handoffs, and tools based on the input content.
   *
   * @param userMessage the input that initiated the swarm
   * @return the swarm configuration to use for this execution
   */
  protected abstract SwarmParams parameters(String userMessage);

  /**
   * The expected result type. When the LLM produces output conforming to this type,
   * the swarm terminates successfully.
   */
  protected abstract Class<R> resultType();

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
