/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm_class2;

import akka.javasdk.swarm_class.Handoff;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for a class-based swarm, returned by {@link Swarm#parameters(String)}.
 *
 * <p>Unlike {@link akka.javasdk.swarm.SwarmParams}, this version does not include
 * {@code userMessage} (it's the input to {@code parameters()}) or {@code resultType}
 * (it's a type parameter on the {@code Swarm<R>} class).
 *
 * <p>Since {@code parameters()} receives the user input, the swarm can dynamically
 * choose different instructions, handoffs, or tools based on the input content.
 */
public final class SwarmParams {

  private final String instructions;
  private final List<Handoff> handoffs;
  private final List<Object> tools;
  private final int maxTurns;

  private SwarmParams(Builder builder) {
    this.instructions = builder.instructions;
    this.handoffs = builder.handoffs != null
        ? Collections.unmodifiableList(builder.handoffs)
        : List.of();
    this.tools = builder.tools != null
        ? Collections.unmodifiableList(builder.tools)
        : List.of();
    this.maxTurns = builder.maxTurns;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String instructions() {
    return instructions;
  }

  public List<Handoff> handoffs() {
    return handoffs;
  }

  public List<Object> tools() {
    return tools;
  }

  public int maxTurns() {
    return maxTurns;
  }

  public static final class Builder {
    private String instructions;
    private List<Handoff> handoffs;
    private List<Object> tools;
    private int maxTurns = 10;

    private Builder() {}

    /** Set the system instructions for the orchestrator LLM. */
    public Builder instructions(String instructions) {
      this.instructions = instructions;
      return this;
    }

    /** Set the handoff targets available to the orchestrator LLM. */
    public Builder handoffs(Handoff... handoffs) {
      this.handoffs = Arrays.asList(handoffs);
      return this;
    }

    /** Set the tools available to the orchestrator LLM. */
    public Builder tools(Object... tools) {
      this.tools = Arrays.asList(tools);
      return this;
    }

    /** Set the maximum number of turns. Defaults to 10. */
    public Builder maxTurns(int maxTurns) {
      this.maxTurns = maxTurns;
      return this;
    }

    public SwarmParams build() {
      if (instructions == null || instructions.isBlank()) {
        throw new IllegalArgumentException("instructions is required");
      }
      return new SwarmParams(this);
    }
  }
}
