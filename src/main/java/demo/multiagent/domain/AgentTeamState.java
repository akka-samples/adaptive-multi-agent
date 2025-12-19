package demo.multiagent.domain;

import akka.javasdk.workflow.pattern.AdaptiveLoopState;
import akka.javasdk.workflow.pattern.WithAdaptiveLoopState;

/**
 * Represents the state of the AgentTeamWorkflow orchestrator.
 * Composes AdaptiveLoopState to support the adaptive loop pattern.
 */
public record AgentTeamState(
  String task,
  AdaptiveLoopState loopState,
  Status status,
  double budgetLimit,
  double currentSpent
) implements WithAdaptiveLoopState<AgentTeamState> {

  public enum Status {
    STARTED,
    EXECUTING,
    COMPLETED,
    FAILED,
  }

  /**
   * Creates an initial state for a new task with default budget limit.
   */
  public static AgentTeamState init(String task) {
    return init(task, 10.0); // Default $10 budget
  }

  /**
   * Creates an initial state for a new task with specified budget limit.
   */
  public static AgentTeamState init(String task, double budgetLimit) {
    return new AgentTeamState(task, AdaptiveLoopState.init(), Status.STARTED, budgetLimit, 0.0);
  }

  @Override
  public String task() {
    return task;
  }

  @Override
  public AdaptiveLoopState loopState() {
    return loopState;
  }

  @Override
  public AgentTeamState withLoopState(AdaptiveLoopState loopState) {
    return new AgentTeamState(task, loopState, status, budgetLimit, currentSpent);
  }

  /**
   * Marks the workflow as completed with a final answer.
   */
  public AgentTeamState complete(String finalAnswer) {
    return new AgentTeamState(
        task,
        loopState.addMessage("FINAL: " + finalAnswer)
                 .addMessage(String.format("BUDGET: Spent $%.2f of $%.2f", currentSpent, budgetLimit)),
        Status.COMPLETED,
        budgetLimit,
        currentSpent
    );
  }

  /**
   * Marks the workflow as failed with a reason.
   */
  public AgentTeamState fail(String reason) {
    return new AgentTeamState(
        task,
        loopState.addMessage("FAILED: " + reason),
        Status.FAILED,
        budgetLimit,
        currentSpent
    );
  }

  /**
   * Updates the status.
   */
  public AgentTeamState withStatus(Status newStatus) {
    return new AgentTeamState(task, loopState, newStatus, budgetLimit, currentSpent);
  }

  /**
   * Adds to the current spent amount.
   */
  public AgentTeamState addSpent(double amount) {
    return new AgentTeamState(task, loopState, status, budgetLimit, currentSpent + amount);
  }

  /**
   * Checks if there's enough budget remaining.
   */
  public boolean hasRemainingBudget(double requiredAmount) {
    return (currentSpent + requiredAmount) <= budgetLimit;
  }

  /**
   * Gets the remaining budget.
   */
  public double remainingBudget() {
    return budgetLimit - currentSpent;
  }
}
