/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.util.Optional;

/**
 * Defines a handoff target for a swarm. A handoff is a delegation from the orchestrator LLM
 * to another agent or a nested swarm.
 */
public sealed interface Handoff {

  /** Optional description used in the LLM tool description. */
  Optional<String> description();

  /**
   * Handoff to an existing agent component.
   *
   * @param agentId the agent's component ID
   * @param description optional description for the LLM tool
   */
  record AgentHandoff(String agentId, Optional<String> description) implements Handoff {
    public AgentHandoff(String agentId) {
      this(agentId, Optional.empty());
    }
  }

  /**
   * Handoff to a nested swarm (composition).
   *
   * @param name optional name for the nested swarm
   * @param params the swarm configuration
   * @param description optional description for the LLM tool
   */
  record SwarmHandoff(Optional<String> name, SwarmParams params, Optional<String> description)
      implements Handoff {
    public SwarmHandoff(SwarmParams params) {
      this(Optional.empty(), params, Optional.empty());
    }

    public SwarmHandoff(String name, SwarmParams params) {
      this(Optional.of(name), params, Optional.empty());
    }
  }

  /** Create a handoff to an agent identified by its component ID. */
  static Handoff toAgent(String agentId) {
    return new AgentHandoff(agentId);
  }

  /**
   * Create a handoff to an agent identified by its class.
   * The component ID is resolved from the {@code @Component} annotation on the class.
   */
  static Handoff toAgent(Class<?> agentClass) {
    // Agent ID lookup from @Component annotation is done by the runtime
    return new AgentHandoff(agentClass.getSimpleName());
  }

  /** Create a handoff to a nested swarm (composition). */
  static Handoff toSwarm(SwarmParams params) {
    return new SwarmHandoff(params);
  }

  /** Create a named handoff to a nested swarm (composition). */
  static Handoff toSwarm(String name, SwarmParams params) {
    return new SwarmHandoff(name, params);
  }

  /** Return a copy with the given description, used in the LLM tool description. */
  default Handoff withDescription(String description) {
    return switch (this) {
      case AgentHandoff a -> new AgentHandoff(a.agentId(), Optional.of(description));
      case SwarmHandoff s -> new SwarmHandoff(s.name(), s.params(), Optional.of(description));
    };
  }
}
