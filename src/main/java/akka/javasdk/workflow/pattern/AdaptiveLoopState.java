package akka.javasdk.workflow.pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete state holder for adaptive loop tracking.
 * Encapsulates all loop-related state: facts, plan, execution history, and counters.
 * <p>
 * This is composed into workflow states, providing a clean separation between loop orchestration
 * state and workflow-specific state.
 */
public record AdaptiveLoopState(
    String facts,
    String plan,
    List<String> messageHistory,
    Map<String, String> agentResponses,
    int stallCount,
    int turnCount,
    int replanCount,
    PendingExecution pendingExecution,
    PendingApproval pendingApproval
) {

  /**
   * Pending agent execution to be performed in the execute step.
   * Can be either a single agent or a parallel group of agents.
   */
  public record PendingExecution(PlanStep step) {
    public PendingExecution(String agentId, String instruction) {
      this(PlanStep.of(agentId, instruction));
    }
  }

  /**
   * Pending human approval during HITL pause.
   * Contains the evaluation result and context for the approval decision.
   */
  public record PendingApproval(
      AdaptiveLoopWorkflow.ProgressEvaluation evaluation,
      String approvalId,
      String timestamp
  ) {}

  /**
   * Creates an initial empty loop state.
   */
  public static AdaptiveLoopState init() {
    return new AdaptiveLoopState(
        "",
        "",
        new ArrayList<>(),
        new HashMap<>(),
        0,
        0,
        0,
        null,
        null
    );
  }

  public AdaptiveLoopState withFacts(String facts) {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount, turnCount, replanCount, pendingExecution, pendingApproval);
  }

  public AdaptiveLoopState withPlan(String plan) {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount, turnCount, replanCount, pendingExecution, pendingApproval);
  }

  public AdaptiveLoopState addMessage(String message) {
    var newHistory = new ArrayList<>(messageHistory);
    newHistory.add(message);
    return new AdaptiveLoopState(facts, plan, newHistory, agentResponses,
        stallCount, turnCount, replanCount, pendingExecution, pendingApproval);
  }

  public AdaptiveLoopState addAgentResponse(String agentId, String response) {
    var newResponses = new HashMap<>(agentResponses);
    newResponses.put(agentId, response);
    return new AdaptiveLoopState(facts, plan, messageHistory, newResponses,
        stallCount, turnCount, replanCount, pendingExecution, pendingApproval);
  }

  public AdaptiveLoopState incrementStallCount() {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount + 1, turnCount, replanCount, pendingExecution, pendingApproval);
  }

  public AdaptiveLoopState resetStallCount() {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        0, turnCount, replanCount, pendingExecution, pendingApproval);
  }

  public AdaptiveLoopState incrementTurnCount() {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount, turnCount + 1, replanCount, pendingExecution, pendingApproval);
  }

  public AdaptiveLoopState startReplanning() {
    return new AdaptiveLoopState(
        facts,
        plan,
        new ArrayList<>(), // Clear message history for new outer loop
        new HashMap<>(), // Clear agent responses
        0, // Reset stall count
        turnCount,
        replanCount + 1,
        null, // Clear pending execution
        null  // Clear pending approval
    );
  }

  public AdaptiveLoopState withPendingExecution(String agentId, String instruction) {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount, turnCount, replanCount, new PendingExecution(agentId, instruction), pendingApproval);
  }

  public AdaptiveLoopState withPendingExecution(PlanStep step) {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount, turnCount, replanCount, new PendingExecution(step), pendingApproval);
  }

  public AdaptiveLoopState clearPendingExecution() {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount, turnCount, replanCount, null, pendingApproval);
  }

  /**
   * Sets pending approval for human-in-the-loop pause.
   * Generates a unique approval ID and timestamp.
   */
  public AdaptiveLoopState withPendingApproval(AdaptiveLoopWorkflow.ProgressEvaluation evaluation) {
    var approval = new PendingApproval(
        evaluation,
        java.util.UUID.randomUUID().toString(),
        java.time.Instant.now().toString()
    );
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount, turnCount, replanCount, pendingExecution, approval);
  }

  /**
   * Clears pending approval after human decision.
   */
  public AdaptiveLoopState clearPendingApproval() {
    return new AdaptiveLoopState(facts, plan, messageHistory, agentResponses,
        stallCount, turnCount, replanCount, pendingExecution, null);
  }
}
