/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class;

import java.util.Optional;

/**
 * Defines a handoff target for a class-based swarm. A handoff is a delegation from the
 * orchestrator LLM to another agent or another swarm.
 *
 * <p>Both agents and swarms can be referenced by component ID (string) or by class.
 * When referenced by class, the component ID is resolved from the {@code @Component}
 * annotation at runtime.
 *
 * <p>Unlike {@link akka.javasdk.swarm.Handoff}, this version does not support inline
 * SwarmParams definitions — all swarm handoffs reference registered swarm classes or IDs.
 */
public sealed interface Handoff {

  /** Optional description used in the LLM tool description. */
  Optional<String> description();

  /**
   * Handoff to an agent component.
   *
   * @param componentId the agent's component ID (or class-derived ID)
   * @param description optional description for the LLM tool
   */
  record AgentHandoff(String componentId, Optional<String> description) implements Handoff {
    public AgentHandoff(String componentId) {
      this(componentId, Optional.empty());
    }
  }

  /**
   * Handoff to another swarm component.
   *
   * @param componentId the swarm's component ID (or class-derived ID)
   * @param description optional description for the LLM tool
   */
  record SwarmHandoff(String componentId, Optional<String> description) implements Handoff {
    public SwarmHandoff(String componentId) {
      this(componentId, Optional.empty());
    }
  }

  // ========== Agent handoffs ==========

  /** Create a handoff to an agent by component ID. */
  static Handoff toAgent(String agentId) {
    return new AgentHandoff(agentId);
  }

  /**
   * Create a handoff to an agent by class.
   * The component ID is resolved from the {@code @Component} annotation.
   */
  static Handoff toAgent(Class<?> agentClass) {
    return new AgentHandoff(resolveComponentId(agentClass));
  }

  // ========== Swarm handoffs ==========

  /** Create a handoff to another swarm by component ID. */
  static Handoff toSwarm(String swarmId) {
    return new SwarmHandoff(swarmId);
  }

  /**
   * Create a handoff to another swarm by class.
   * The component ID is resolved from the {@code @Component} annotation.
   */
  static Handoff toSwarm(Class<?> swarmClass) {
    return new SwarmHandoff(resolveComponentId(swarmClass));
  }

  /** Return a copy with the given description, used in the LLM tool description. */
  default Handoff withDescription(String description) {
    return switch (this) {
      case AgentHandoff a -> new AgentHandoff(a.componentId(), Optional.of(description));
      case SwarmHandoff s -> new SwarmHandoff(s.componentId(), Optional.of(description));
    };
  }

  /** Resolve component ID from class — placeholder, actual lookup done by runtime. */
  private static String resolveComponentId(Class<?> componentClass) {
    // In real implementation: look up @Component(id = "...") annotation
    return componentClass.getSimpleName();
  }
}
