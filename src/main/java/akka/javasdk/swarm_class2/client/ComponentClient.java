/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class2.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.agent.Agent;
import akka.javasdk.client.AgentClient;
import akka.javasdk.client.EventSourcedEntityClient;
import akka.javasdk.client.KeyValueEntityClient;
import akka.javasdk.client.TimedActionClient;
import akka.javasdk.client.ViewClient;
import akka.javasdk.client.WorkflowClient;
import akka.javasdk.swarm_class2.Swarm;

/**
 * Component client with support for class-based swarms (swarm_class2 variant).
 *
 * <p>Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface ComponentClient {
  TimedActionClient forTimedAction();
  KeyValueEntityClient forKeyValueEntity(String keyValueEntityId);
  EventSourcedEntityClient forEventSourcedEntity(String eventSourcedEntityId);
  WorkflowClient forWorkflow(String workflowId);
  ViewClient forView();
  AgentClient forAgent();

  /**
   * Select a class-based {@link Swarm} as a call target component.
   *
   * @param swarmClass the concrete swarm class to target
   * @param swarmId unique identifier for this swarm instance
   * @param <A> the input type accepted by the swarm
   * @param <B> the result type produced by the swarm
   */
  <A, B> SwarmClient<A, B> forSwarm(Class<? extends Swarm<A, B>> swarmClass, String swarmId);
}
