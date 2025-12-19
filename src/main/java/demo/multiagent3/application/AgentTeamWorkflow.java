package demo.multiagent3.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.workflow.pattern.PlanStep;
import akka.javasdk.workflow.pattern.SequentialPlanWorkflow;
import demo.multiagent3.domain.AgentRequest;
import demo.multiagent3.domain.AgentTeamState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.time.Duration.ofSeconds;

@Component(id = "agent-team3")
public class AgentTeamWorkflow extends SequentialPlanWorkflow<AgentTeamState, AgentTeamWorkflow> {

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  public record Request(String userId, String message) {}

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(60))
        .defaultStepRecovery(maxRetries(2).failoverTo(AgentTeamWorkflow::errorStep))
        .build();
  }

  // ========== Command Handlers ==========

  public Effect<Done> start(Request request) {
    if (currentState() == null) {
      return effects()
          .updateState(AgentTeamState.init(request.userId(), request.message()))
          .transitionTo(planStart())
          .thenReply(Done.getInstance());
    } else {
      return effects()
          .error("Workflow '" + commandContext().workflowId() + "' already started");
    }
  }

  public ReadOnlyEffect<String> getAnswer() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else if (currentState().finalAnswer().isEmpty()) {
      return effects().error("Workflow not completed yet");
    } else {
      return effects().reply(currentState().finalAnswer());
    }
  }

  public ReadOnlyEffect<AgentTeamState> getState() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else {
      return effects().reply(currentState());
    }
  }

  // ========== Sequential Plan Callbacks ==========

  @Override
  protected AgentTeamState createPlan() {
    logger.info("Creating static plan for query: {}", currentState().userQuery());

    // Static plan: weather agent first, then activity agent
    List<PlanStep> steps = List.of(
        PlanStep.of("weather-agent", currentState().userQuery()),
        PlanStep.of("activity-agent", currentState().userQuery())
    );

    logger.info("Created static plan with {} steps", steps.size());

    return currentState().withPlanState(
        currentState().planState().withSteps(steps)
    );
  }

  @Override
  protected AgentExecutionEffect<?, AgentTeamState> executeAgent(
      String agentId, String instruction) {
    return AgentExecutionEffect
        .call(() -> {
          logger.info("Starting execution of agent: {}, instruction: {}", agentId, instruction);
          var request = new AgentRequest(currentState().userId(), instruction);
          DynamicMethodRef<AgentRequest, String> call = componentClient
              .forAgent()
              .inSession(sessionId())
              .dynamicCall(agentId);

          String response = call.invoke(request);
          logger.info("Agent {} completed: {}", agentId, response);
          return response;
        })
        .updateState((response, state) -> state.addResponse(agentId, response));
  }

  @Override
  protected AgentTeamState summarize() {
    logger.info("Summarizing workflow - using activity-agent response as final answer");

    // The activity agent response is the final answer
    String finalAnswer = currentState().agentResponses().get("activity-agent");

    if (finalAnswer == null || finalAnswer.isEmpty()) {
      logger.warn("No activity-agent response found");
      return currentState().complete("Unable to generate activity suggestions");
    }

    return currentState().complete(finalAnswer);
  }

  @Override
  protected AgentTeamState handleFailure(String reason) {
    return currentState().fail(reason);
  }

  // ========== Helper Methods ==========

  private String sessionId() {
    return commandContext().workflowId();
  }
}
