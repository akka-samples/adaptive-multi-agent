/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for a swarm execution. Created via {@link #builder()}.
 */
public final class SwarmParams {

  private final String instructions;
  private final String userMessage;
  private final Class<?> responseType;
  private final List<Handoff> handoffs;
  private final List<Object> tools;
  private final int maxTurns;

  private SwarmParams(Builder builder) {
    this.instructions = builder.instructions;
    this.userMessage = builder.userMessage;
    this.responseType = builder.responseType;
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

  public Optional<String> instructions() {
    return Optional.ofNullable(instructions);
  }

  public String userMessage() {
    return userMessage;
  }

  public Optional<Class<?>> responseType() {
    return Optional.ofNullable(responseType);
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
    private String userMessage;
    private Class<?> responseType;
    private List<Handoff> handoffs;
    private List<Object> tools;
    private int maxTurns = 10;

    private Builder() {}

    /** Set the system instructions for the orchestrator LLM. */
    public Builder instructions(String instructions) {
      this.instructions = instructions;
      return this;
    }

    /** Set the user message that initiates the swarm. */
    public Builder userMessage(String userMessage) {
      this.userMessage = userMessage;
      return this;
    }

    /**
     * Set the expected response type. When the LLM produces output conforming to this type,
     * the swarm terminates successfully.
     */
    public Builder responseAs(Class<?> responseType) {
      this.responseType = responseType;
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

    /**
     * Set the maximum number of turns before the swarm terminates.
     * Defaults to 10.
     */
    public Builder maxTurns(int maxTurns) {
      this.maxTurns = maxTurns;
      return this;
    }

    public SwarmParams build() {
      if (userMessage == null || userMessage.isBlank()) {
        throw new IllegalArgumentException("userMessage is required");
      }
      return new SwarmParams(this);
    }
  }
}
