/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.client.ComponentMethodRef;
import akka.javasdk.client.ComponentMethodRef1;
import akka.javasdk.swarm.Swarm;

/**
 * Client for invoking swarm methods within a session.
 *
 * <p>Uses the same {@code .method(Swarm::xxx).invoke()} pattern as other component clients.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 */
@DoNotInherit
public interface SwarmClientInSession {

  /**
   * Pass in a Swarm method reference that takes no arguments, e.g. {@code Swarm::getResult}
   */
  <R> ComponentMethodRef<R> method(Function<Swarm, Swarm.Effect<R>> methodRef);

  /**
   * Pass in a Swarm method reference that takes one argument, e.g. {@code Swarm::run}
   */
  <A1, R> ComponentMethodRef1<A1, R> method(Function2<Swarm, A1, Swarm.Effect<R>> methodRef);
}
