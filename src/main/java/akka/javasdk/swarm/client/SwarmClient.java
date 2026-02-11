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
 * Client for configuring and invoking swarm methods. Returned by
 * {@code componentClient.forSwarm(swarmId)}.
 *
 * <p>The swarm ID is both the unique instance identifier and the session ID for
 * conversational memory shared across agents within the swarm.
 *
 * <p>Optionally, component metadata (id, name, description) can be set for observability.
 * These correspond to the {@code @Component} annotation fields on regular Akka components.
 *
 * <p>Example usage:
 * <pre>{@code
 * componentClient
 *     .forSwarm(swarmId)
 *     .withComponentId("activity-planner")
 *     .method(Swarm::run)
 *     .invoke(params);
 * }</pre>
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client.
 */
@DoNotInherit
public interface SwarmClient {

  /**
   * Set the component ID for this swarm instance, used for observability and routing.
   * Corresponds to the {@code id} field in {@code @Component} on regular Akka components.
   */
  SwarmClient withComponentId(String componentId);

  /**
   * Set a human-readable name for this swarm instance, used for observability.
   * Corresponds to the {@code name} field in {@code @Component} on regular Akka components.
   */
  SwarmClient withComponentName(String name);

  /**
   * Set a description for this swarm instance, used for observability.
   * Corresponds to the {@code description} field in {@code @Component} on regular Akka components.
   */
  SwarmClient withComponentDescription(String description);

  /**
   * Pass in a Swarm method reference that takes no arguments, e.g. {@code Swarm::getResult}
   */
  <R> ComponentMethodRef<R> method(Function<Swarm, Swarm.Effect<R>> methodRef);

  /**
   * Pass in a Swarm method reference that takes one argument, e.g. {@code Swarm::run}
   */
  <A1, R> ComponentMethodRef1<A1, R> method(Function2<Swarm, A1, Swarm.Effect<R>> methodRef);
}
