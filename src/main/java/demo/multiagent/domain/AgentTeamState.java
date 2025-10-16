package demo.multiagent.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the state of the AgentTeamWorkflow orchestrator.
 */
public record AgentTeamState(
  String task,
  String facts,
  String plan,
  List<String> messageHistory,
  Map<String, String> agentResponses,
  int stallCount,
  int roundCount,
  int replanCount,
  Status status
) {
  public enum Status {
    STARTED,
    GATHERING_FACTS,
    CREATING_PLAN,
    EXECUTING,
    REPLANNING,
    COMPLETED,
    FAILED,
  }

  /**
   * Creates an initial state for a new task.
   */
  public static AgentTeamState init(String task) {
    return new AgentTeamState(
      task,
      "",
      "",
      new ArrayList<>(),
      new HashMap<>(),
      0,
      0,
      0,
      Status.STARTED
    );
  }

  /**
   * Updates the facts and moves to planning state.
   */
  public AgentTeamState withFacts(String facts) {
    return new AgentTeamState(
      task,
      facts,
      plan,
      messageHistory,
      agentResponses,
      stallCount,
      roundCount,
      replanCount,
      Status.CREATING_PLAN
    );
  }

  /**
   * Updates the plan and moves to executing state.
   */
  public AgentTeamState withPlan(String plan) {
    return new AgentTeamState(
      task,
      facts,
      plan,
      messageHistory,
      agentResponses,
      stallCount,
      roundCount,
      replanCount,
      Status.EXECUTING
    );
  }

  /**
   * Adds a message to the history.
   */
  public AgentTeamState addMessage(String message) {
    var newHistory = new ArrayList<>(messageHistory);
    newHistory.add(message);
    return new AgentTeamState(
      task,
      facts,
      plan,
      newHistory,
      agentResponses,
      stallCount,
      roundCount,
      replanCount,
      status
    );
  }

  /**
   * Adds an agent response to the history.
   */
  public AgentTeamState addAgentResponse(String agentId, String response) {
    var newResponses = new HashMap<>(agentResponses);
    newResponses.put(agentId, response);
    return new AgentTeamState(
      task,
      facts,
      plan,
      messageHistory,
      newResponses,
      stallCount,
      roundCount,
      replanCount,
      status
    );
  }

  /**
   * Increments the stall count.
   */
  public AgentTeamState incrementStallCount() {
    return new AgentTeamState(
      task,
      facts,
      plan,
      messageHistory,
      agentResponses,
      stallCount + 1,
      roundCount,
      replanCount,
      status
    );
  }

  /**
   * Resets the stall count.
   */
  public AgentTeamState resetStallCount() {
    return new AgentTeamState(
      task,
      facts,
      plan,
      messageHistory,
      agentResponses,
      0,
      roundCount,
      replanCount,
      status
    );
  }

  /**
   * Increments the round count.
   */
  public AgentTeamState incrementRoundCount() {
    return new AgentTeamState(
      task,
      facts,
      plan,
      messageHistory,
      agentResponses,
      stallCount,
      roundCount + 1,
      replanCount,
      status
    );
  }

  /**
   * Marks as replanning and resets execution state.
   */
  public AgentTeamState startReplanning() {
    return new AgentTeamState(
      task,
      facts,
      plan,
      new ArrayList<>(), // Clear message history for new outer loop
      new HashMap<>(), // Clear agent responses
      0, // Reset stall count
      roundCount,
      replanCount + 1, // Increment replan count
      Status.REPLANNING
    );
  }

  /**
   * Marks the workflow as completed.
   */
  public AgentTeamState complete(String finalAnswer) {
    return addMessage("FINAL: " + finalAnswer).withStatus(Status.COMPLETED);
  }

  /**
   * Marks the workflow as failed.
   */
  public AgentTeamState fail(String reason) {
    return addMessage("FAILED: " + reason).withStatus(Status.FAILED);
  }

  /**
   * Updates the status.
   */
  public AgentTeamState withStatus(Status newStatus) {
    return new AgentTeamState(
      task,
      facts,
      plan,
      messageHistory,
      agentResponses,
      stallCount,
      roundCount,
      replanCount,
      newStatus
    );
  }
}
