package akka.javasdk.workflow.pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal state holder for sequential plan execution.
 * Contains only what the framework needs to orchestrate step execution.
 * Application-specific data (responses, history, etc.) should be stored in the concrete workflow state.
 */
public record SequentialPlanState(
    List<PlanStep> remainingSteps
) {

  /**
   * Creates an initial empty state.
   */
  public static SequentialPlanState init() {
    return new SequentialPlanState(new ArrayList<>());
  }

  /**
   * Returns the next step to execute.
   */
  public PlanStep nextStep() {
    if (remainingSteps.isEmpty()) {
      throw new IllegalStateException("No more steps in the plan");
    }
    return remainingSteps.getFirst();
  }

  /**
   * Checks if there are more steps to execute.
   */
  public boolean hasMoreSteps() {
    return !remainingSteps.isEmpty();
  }

  /**
   * Sets the plan steps for execution.
   */
  public SequentialPlanState withSteps(List<PlanStep> steps) {
    var mutableSteps = new ArrayList<>(steps);
    return new SequentialPlanState(mutableSteps);
  }

  /**
   * Removes the first step from the queue and returns updated state.
   */
  public SequentialPlanState removeFirstStep() {
    var newSteps = new ArrayList<>(remainingSteps);
    newSteps.removeFirst();
    return new SequentialPlanState(newSteps);
  }
}
