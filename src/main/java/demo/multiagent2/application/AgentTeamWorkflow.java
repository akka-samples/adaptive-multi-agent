package demo.multiagent2.application;

import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.workflow.pattern.SequentialPlanWorkflow;
import demo.multiagent2.domain.AgentRequest;
import demo.multiagent2.domain.AgentTeamState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "agent-team2")
public class AgentTeamWorkflow extends SequentialPlanWorkflow<AgentTeamState, AgentTeamWorkflow> {

  public record Request(String userId, String message) {}

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  private final ComponentClient componentClient;

  public AgentTeamWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(30))
        .defaultStepRecovery(maxRetries(1).failoverTo(AgentTeamWorkflow::errorStep))
        .stepRecovery(
            AgentTeamWorkflow::createPlanStep,
            maxRetries(1).failoverTo(AgentTeamWorkflow::summarizeStep)
        )
        .build();
  }

  // ========== Configuration ==========

  @Override
  protected int maxSteps() {
    return 10;
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

  public Effect<Done> runAgain() {
    if (currentState() != null) {
      return effects()
          .updateState(AgentTeamState.init(currentState().userId(), currentState().userQuery()))
          .transitionTo(planStart())
          .thenReply(Done.getInstance());
    } else {
      return effects()
          .error("Workflow '" + commandContext().workflowId() + "' has not been started");
    }
  }

  public ReadOnlyEffect<String> getAnswer() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
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
    logger.info("Selecting agents and creating plan for query: {}", currentState().userQuery());

    // Select agents
    var selection = componentClient
        .forAgent()
        .inSession(sessionId())
        .method(SelectorAgent::selectAgents)
        .invoke(currentState().userQuery());

    logger.info("Selected {} agent(s)", selection.agents().size());

    // Create plan
    var plan = componentClient
        .forAgent()
        .inSession(sessionId())
        .method(PlannerAgent::createPlan)
        .invoke(new PlannerAgent.Request(currentState().userQuery(), selection));

    logger.info("Created plan with {} step(s)", plan.steps().size());

    // PlannerAgent now returns DSL PlanSteps directly, including parallel execution decisions
    // Store selected agents in application state, steps in plan state
    return currentState()
        .withSelectedAgents(selection.agents())
        .withPlanState(currentState().planState().withSteps(plan.steps()));
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
          if (response.startsWith("ERROR")) {
            throw new RuntimeException("Agent '" + agentId + "' responded with error: " + response);
          }
          logger.info("Agent {} completed successfully", agentId);
          return response;
        })
        .updateState((response, state) -> state.addResponse(agentId, response));
  }

  @Override
  protected AgentTeamState summarize() {
    logger.info("Summarizing {} agent response(s)", currentState().agentResponses().size());

    var agentAnswers = currentState().agentResponses().values();

    if (agentAnswers.isEmpty()) {
      logger.warn("No agent responses to summarize");
      return currentState().complete("Couldn't find any agent(s) able to respond to the original query.");
    }

    String finalAnswer = componentClient
        .forAgent()
        .inSession(sessionId())
        .method(SummarizerAgent::summarize)
        .invoke(new SummarizerAgent.Request(currentState().userQuery(), agentAnswers));

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
