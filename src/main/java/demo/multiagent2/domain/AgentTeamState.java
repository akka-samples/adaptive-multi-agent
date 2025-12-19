package demo.multiagent2.domain;

import akka.javasdk.workflow.pattern.SequentialPlanState;
import akka.javasdk.workflow.pattern.WithSequentialPlanState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the state of the AgentTeamWorkflow.
 * Composes SequentialPlanState to support the sequential plan pattern.
 */
public record AgentTeamState(
    String userId,
    String userQuery,
    List<String> selectedAgents,
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
    return new AgentTeamState(userId, query, List.of(), new HashMap<>(), "", SequentialPlanState.init(), Status.STARTED);
  }

  @Override
  public SequentialPlanState planState() {
    return planState;
  }

  @Override
  public AgentTeamState withPlanState(SequentialPlanState planState) {
    return new AgentTeamState(userId, userQuery, selectedAgents, agentResponses, finalAnswer, planState, status);
  }

  /**
   * Stores the selected agents.
   */
  public AgentTeamState withSelectedAgents(List<String> agents) {
    return new AgentTeamState(userId, userQuery, agents, agentResponses, finalAnswer, planState, status);
  }

  /**
   * Adds an agent response.
   */
  public AgentTeamState addResponse(String agentId, String response) {
    var newResponses = new HashMap<>(agentResponses);
    newResponses.put(agentId, response);
    return new AgentTeamState(userId, userQuery, selectedAgents, newResponses, finalAnswer, planState, status);
  }

  /**
   * Marks the workflow as completed with a final answer.
   */
  public AgentTeamState complete(String answer) {
    return new AgentTeamState(
        userId,
        userQuery,
        selectedAgents,
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
        selectedAgents,
        agentResponses,
        finalAnswer,
        planState,
        Status.FAILED
    );
  }
}
