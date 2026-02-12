/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class.client;

import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.javasdk.swarm.SwarmEvent;
import akka.javasdk.swarm_class.SwarmParams;
import akka.javasdk.swarm_class.SwarmResult;
import akka.stream.javadsl.Source;

/**
 * Client for invoking swarm operations. Returned by
 * {@code componentClient.forSwarm(MySwarm.class, swarmId)}.
 *
 * <p>Unlike other component clients that use the {@code .method(Ref::method).invoke()} pattern,
 * the swarm client exposes concrete methods directly since the operations are fixed and known.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Start a swarm
 * componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).run("Plan weekend activities");
 *
 * // Start with overridden parameters (e.g. extra handoffs, different instructions)
 * componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId)
 *     .withParameters(SwarmParams.builder()
 *         .instructions("Focus on indoor activities only")
 *         .handoffs(Handoff.toAgent("indoor-agent"))
 *         .build())
 *     .run("Plan weekend activities");
 *
 * // Get the result
 * SwarmResult<ActivityRecommendation> result =
 *     componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).getResult();
 *
 * switch (result) {
 *   case SwarmResult.Completed<ActivityRecommendation> c -> handleResult(c.result());
 *   case SwarmResult.Running<ActivityRecommendation> r -> waitAndRetry();
 *   case SwarmResult.Paused<ActivityRecommendation> p -> handlePause(p.reason());
 *   case SwarmResult.Failed<ActivityRecommendation> f -> handleFailure(f.reason());
 *   case SwarmResult.Stopped<ActivityRecommendation> s -> handleStopped(s.reason());
 * }
 * }</pre>
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 *
 * @param <A> the input type accepted by {@link #run(Object)}
 * @param <B> the result type produced when the swarm completes
 */
@DoNotInherit
public interface SwarmClient<A, B> {

  /**
   * Override the swarm's parameters at the call site. The provided {@link SwarmParams}
   * replaces the configuration defined by the swarm class (instructions, handoffs, tools,
   * maxTurns). The swarm's {@code resultType()} is still determined by the class.
   *
   * <p>This is useful for customizing a swarm's behavior without creating a new subclass,
   * e.g. adding extra handoffs, changing instructions, or adjusting maxTurns.
   */
  SwarmClient<A, B> withParameters(SwarmParams params);

  /** Start the swarm with the given input. */
  void run(A input);

  /** Get the current result/status as a fully-typed SwarmResult. */
  SwarmResult<B> getResult();

  /** Resume a paused swarm with additional context (e.g. human approval). */
  void resume(String message);

  /** Stop the swarm permanently (terminal state). */
  void stop(String reason);

  /** Subscribe to real-time notification events from this swarm. */
  Source<SwarmEvent, NotUsed> events();
}
