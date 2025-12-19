package demo.multiagent3.domain;

import akka.javasdk.workflow.pattern.SequentialPlanState;
import akka.javasdk.workflow.pattern.WithSequentialPlanState;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of the AgentTeamWorkflow.
 * Uses SequentialPlanState for a static, fixed sequence of agents.
 */
public record AgentTeamState(
    String userId,
    String userQuery,
    Map<String, String> agentResponses,
    String finalAnswer,
    SequentialPlanState planState,
    Status status
) implements WithSequentialPlanState<AgentTeamState> {

  public enum Status {
    STARTED,
    COMPLETED,
    FAILED
  }

  /**
   * Creates an initial state for a new task.
   */
  public static AgentTeamState init(String userId, String query) {
    return new AgentTeamState(userId, query, new HashMap<>(), "", SequentialPlanState.init(), Status.STARTED);
  }

  @Override
  public SequentialPlanState planState() {
    return planState;
  }

  @Override
  public AgentTeamState withPlanState(SequentialPlanState planState) {
    return new AgentTeamState(userId, userQuery, agentResponses, finalAnswer, planState, status);
  }

  /**
   * Adds an agent response.
   */
  public AgentTeamState addResponse(String agentId, String response) {
    var newResponses = new HashMap<>(agentResponses);
    newResponses.put(agentId, response);
    return new AgentTeamState(userId, userQuery, newResponses, finalAnswer, planState, status);
  }

  /**
   * Marks the workflow as completed with a final answer.
   */
  public AgentTeamState complete(String answer) {
    return new AgentTeamState(
        userId,
        userQuery,
        agentResponses,
        answer,
        planState,
        Status.COMPLETED
    );
  }

  /**
   * Marks the workflow as failed with a reason.
   */
  public AgentTeamState fail(String reason) {
    return new AgentTeamState(
        userId,
        userQuery,
        agentResponses,
        finalAnswer,
        planState,
        Status.FAILED
    );
  }
}
