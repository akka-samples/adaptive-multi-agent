/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class2.client;

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
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 *
 * @param <A> the input type accepted by {@link #run(Object)}
 * @param <B> the result type produced when the swarm completes
 */
@DoNotInherit
public interface SwarmClient<A, B> {

  /**
   * Override the swarm's parameters at the call site. The provided {@link SwarmParams}
   * replaces the configuration returned by the swarm's {@code parameters()} method.
   * The swarm's {@code resultType()} is still determined by the class.
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
