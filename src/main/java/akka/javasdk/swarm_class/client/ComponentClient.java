/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.agent.Agent;
import akka.javasdk.client.AgentClient;
import akka.javasdk.client.EventSourcedEntityClient;
import akka.javasdk.client.KeyValueEntityClient;
import akka.javasdk.client.TimedActionClient;
import akka.javasdk.client.ViewClient;
import akka.javasdk.client.WorkflowClient;
import akka.javasdk.swarm_class.Swarm;

/**
 * Utility to send requests to other components by composing a call that can be executed by the
 * runtime. To compose a call:
 *
 * <ol>
 *   <li>select component type (and pass id if necessary)
 *   <li>select component method, by using Java method reference operator (::)
 *   <li>provide a request parameter (if required)
 * </ol>
 *
 * <p>Example of use on a cross-component call:
 *
 * <pre>{@code
 * componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).run("Plan weekend activities");
 * }</pre>
 *
 * Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface ComponentClient {
  /** Select {@link akka.javasdk.timedaction.TimedAction} as a call target component. */
  TimedActionClient forTimedAction();

  /**
   * Select {@link akka.javasdk.keyvalueentity.KeyValueEntity} as a call target component.
   *
   * @param keyValueEntityId - key value entity id used to create a call. Must not be null or empty
   *     string.
   */
  KeyValueEntityClient forKeyValueEntity(String keyValueEntityId);

  /**
   * Select {@link akka.javasdk.eventsourcedentity.EventSourcedEntity} as a call target component.
   *
   * @param eventSourcedEntityId - event sourced entity id used to create a call. Must not be null
   *     or empty string.
   */
  EventSourcedEntityClient forEventSourcedEntity(String eventSourcedEntityId);

  /**
   * Select {@link akka.javasdk.workflow.Workflow} as a call target component.
   *
   * @param workflowId - workflow id used to create a call. Must not be null or empty string.
   */
  WorkflowClient forWorkflow(String workflowId);

  /** Select {@link akka.javasdk.view.View} as a call target component. */
  ViewClient forView();

  /** Select {@link Agent} as a call target component. */
  AgentClient forAgent();

  /**
   * Select a class-based {@link Swarm} as a call target component.
   *
   * <p>The swarm class determines the input type {@code A} and result type {@code B}.
   * The swarm ID is both the unique instance identifier and the session ID for
   * conversational memory shared across agents within the swarm.
   *
   * <p>Example:
   * <pre>{@code
   * componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).run(input);
   * }</pre>
   *
   * @param swarmClass the concrete swarm class to target
   * @param swarmId unique identifier for this swarm instance
   * @param <A> the input type accepted by the swarm
   * @param <B> the result type produced by the swarm
   */
  <A, B> SwarmClient<A, B> forSwarm(Class<? extends Swarm<A, B>> swarmClass, String swarmId);
}
