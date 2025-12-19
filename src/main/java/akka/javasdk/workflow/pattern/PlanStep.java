package akka.javasdk.workflow.pattern;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Represents a step (or group of parallel steps) in a sequential execution plan.
 * <p>
 * Use the static factory methods for clean API:
 * <ul>
 *   <li>{@code PlanStep.of("agent-id", "instruction")} - sequential step</li>
 *   <li>{@code PlanStep.parallel(PlanStep.of(...), PlanStep.of(...))} - parallel group</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PlanStep.Sequential.class),
    @JsonSubTypes.Type(value = PlanStep.Parallel.class)
})
public sealed interface PlanStep {

  /**
   * A single sequential step that executes one agent.
   */
  record Sequential(String agentId, String instruction) implements PlanStep {}

  /**
   * A group of steps that execute in parallel.
   * All steps in the group complete before proceeding to the next plan step.
   */
  record Parallel(List<Sequential> steps) implements PlanStep {}

  /**
   * Creates a sequential step (most common case).
   *
   * @param agentId the agent to execute
   * @param instruction the instruction for the agent
   * @return a sequential plan step
   */
  static Sequential of(String agentId, String instruction) {
    return new Sequential(agentId, instruction);
  }

  /**
   * Creates a parallel group of steps.
   * All steps in the group execute concurrently.
   *
   * @param steps the steps to execute in parallel
   * @return a parallel plan step
   */
  static Parallel parallel(Sequential... steps) {
    return new Parallel(List.of(steps));
  }

  /**
   * Creates a parallel group of steps.
   * All steps in the group execute concurrently.
   *
   * @param steps the steps to execute in parallel
   * @return a parallel plan step
   */
  static Parallel parallel(List<Sequential> steps) {
    return new Parallel(steps);
  }
}
