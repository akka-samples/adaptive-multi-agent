package demo.multiagent.application;

import static demo.multiagent.domain.AgentTeamState.Status.COMPLETED;
import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.workflow.Workflow;
import demo.multiagent.domain.AgentRequest;
import demo.multiagent.domain.AgentTeamState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent orchestrator workflow that implements the
 * <a href="https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/magentic-one.html">MagenticOne pattern</a>.
 * <p>
 * Outer loop: Gather facts → Create plan → Enter inner loop
 * Inner loop: Evaluate progress → Execute agent → Loop/Replan/Finish
 * <p>
 * Key features:
 * - Adaptive: Re-evaluates progress after each agent response
 * - Detects stalling and re-plans when stuck
 * - Selects next agent dynamically based on current state
 * - Maintains evolving fact sheet that updates as it learns
 */
@Component(id = "agent-team")
public class AgentTeamWorkflow extends Workflow<AgentTeamState> {

  public record Request(String userId, String message) {}

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);
  private static final int MAX_TURNS = 15;
  private static final int MAX_STALLS = 3;
  private static final int MAX_REPLANS = 2;

  private final ComponentClient componentClient;
  private final AgentRegistry agentRegistry;

  public AgentTeamWorkflow(ComponentClient componentClient, AgentRegistry agentRegistry) {
    this.componentClient = componentClient;
    this.agentRegistry = agentRegistry;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
      .defaultStepTimeout(ofSeconds(60)) // Long timeout for LLM calls
      .defaultStepRecovery(maxRetries(1).failoverTo(AgentTeamWorkflow::errorStep))
      .build();
  }

  // ========== Command Handlers ==========

  public Effect<Done> start(Request request) {
    if (currentState() == null) {
      return effects()
        .updateState(AgentTeamState.init(request.message()))
        .transitionTo(AgentTeamWorkflow::gatherFactsStep)
        .thenReply(Done.getInstance());
    } else {
      return effects()
        .error("Workflow '" + commandContext().workflowId() + "' already started");
    }
  }

  public ReadOnlyEffect<String> getAnswer() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else if (currentState().status() != COMPLETED) {
      return effects()
        .error("Workflow not completed yet. Status: " + currentState().status());
    } else {
      // Extract final answer from message history
      var messages = currentState().messageHistory();
      for (int i = messages.size() - 1; i >= 0; i--) {
        if (messages.get(i).startsWith("FINAL: ")) {
          return effects().reply(messages.get(i).substring(7));
        }
      }
      return effects().reply("Workflow completed but no final answer found");
    }
  }

  public ReadOnlyEffect<AgentTeamState> getState() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    } else {
      return effects().reply(currentState());
    }
  }

  // ========== Outer Loop: Gather Facts & Create Plan ==========

  @StepName("gather-facts")
  private StepEffect gatherFactsStep() {
    logger.info("OUTER LOOP: Gathering facts for task: {}", currentState().task());

    var facts = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(LedgerAgent::process)
      .invoke(new LedgerAgent.GatherFactsRequest(currentState().task()));

    logger.info("Gathered facts:\n{}", facts);
    return stepEffects()
      .updateState(currentState().withFacts(facts))
      .thenTransitionTo(AgentTeamWorkflow::createPlanStep);
  }

  @StepName("create-plan")
  private StepEffect createPlanStep() {
    logger.info("OUTER LOOP: Creating plan");
    var teamDescription = getTeamDescription();

    var plan = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(LedgerAgent::process)
      .invoke(
        new LedgerAgent.CreatePlanRequest(
          currentState().task(),
          teamDescription,
          currentState().facts()
        )
      );

    logger.info("Created plan:\n{}", plan);

    // Update state with new plan and task ledger
    var ledgerMessage = buildTaskLedger(
      currentState().task(),
      teamDescription,
      currentState().facts(),
      plan
    );
    var newState = currentState().withPlan(plan).addMessage("TASK_LEDGER: " + ledgerMessage);

    return stepEffects()
      .updateState(newState)
      .thenTransitionTo(AgentTeamWorkflow::evaluateProgressStep);
  }

  // ========== Inner Loop: Evaluate Progress & Execute ==========

  @StepName("evaluate-progress")
  private StepEffect evaluateProgressStep() {
    logger.info(
      "INNER LOOP: Evaluating progress (round {}, stall count {})",
      currentState().roundCount(),
      currentState().stallCount()
    );

    // Check max turns limit
    if (currentState().roundCount() >= MAX_TURNS) {
      logger.warn("Max turns ({}) reached", MAX_TURNS);
      return stepEffects()
        .updateState(currentState().fail("Maximum rounds reached"))
        .thenTransitionTo(AgentTeamWorkflow::finalAnswerStep);
    }

    var newState = currentState().incrementRoundCount();
    var workers = agentRegistry.agentsWithRole("worker");
    var participantIds = workers.stream().map(AgentRegistry.AgentInfo::id).toList();

    // If single agent team, skip orchestrator and use it directly
    if (participantIds.size() == 1) {
      var agentId = participantIds.getFirst();
      logger.info("Single agent team - executing {}", agentId);
      var instruction = "Address the task: " + currentState().task();
      newState = newState.addMessage("ORCHESTRATOR: " + instruction);
      return stepEffects()
        .updateState(newState)
        .thenTransitionTo(AgentTeamWorkflow::executeAgentStep)
        .withInput(new AgentExecution(agentId, instruction));
    }

    // Call orchestrator to evaluate progress
    var teamDescription = getTeamDescription();
    var evaluation = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(OrchestratorAgent::evaluateProgress)
      .invoke(
        new OrchestratorAgent.Request(
          currentState().task(),
          teamDescription,
          participantIds,
          newState.roundCount()
        )
      );

    logger.info(
      "Progress evaluation: satisfied={}, inLoop={}, makingProgress={}, nextSpeaker={}",
      evaluation.isRequestSatisfied().answer(),
      evaluation.isInLoop().answer(),
      evaluation.isProgressBeingMade().answer(),
      evaluation.nextSpeaker().answer()
    );

    // Check if request is satisfied
    if (evaluation.isRequestSatisfied().answer()) {
      logger.info("Request satisfied: {}", evaluation.isRequestSatisfied().reason());
      return stepEffects()
        .updateState(
          newState.addMessage("SATISFIED: " + evaluation.isRequestSatisfied().reason())
        )
        .thenTransitionTo(AgentTeamWorkflow::finalAnswerStep);
    }

    // Track stalling
    if (!evaluation.isProgressBeingMade().answer() || evaluation.isInLoop().answer()) {
      newState = newState.incrementStallCount();
      logger.warn(
        "Stall detected (count: {}): progress={}, loop={}",
        newState.stallCount(),
        evaluation.isProgressBeingMade().reason(),
        evaluation.isInLoop().reason()
      );
    } else {
      newState = newState.resetStallCount();
    }

    // Check if too many stalls - need to replan
    if (newState.stallCount() >= MAX_STALLS) {
      // Check if we've replanned too many times
      if (newState.replanCount() >= MAX_REPLANS) {
        logger.warn("Max replans ({}) reached - giving up", MAX_REPLANS);
        return stepEffects()
          .updateState(newState.fail("Maximum replanning attempts reached"))
          .thenTransitionTo(AgentTeamWorkflow::finalAnswerStep);
      }

      logger.warn(
        "Max stalls ({}) reached - replanning (attempt {})",
        MAX_STALLS,
        newState.replanCount() + 1
      );
      return stepEffects()
        .updateState(newState.startReplanning())
        .thenTransitionTo(AgentTeamWorkflow::updateLedgerStep);
    }

    // Continue with next agent
    String nextAgentId = evaluation.nextSpeaker().answer();
    String instruction = evaluation.instructionOrQuestion().answer();

    newState = newState.addMessage("ORCHESTRATOR: " + instruction);
    logger.info("Next agent: {} - {}", nextAgentId, instruction);

    return stepEffects()
      .updateState(newState)
      .thenTransitionTo(AgentTeamWorkflow::executeAgentStep)
      .withInput(new AgentExecution(nextAgentId, instruction));
  }

  public record AgentExecution(String agentId, String instruction) {}

  @StepName("execute-agent")
  private StepEffect executeAgentStep(AgentExecution execution) {
    logger.info(
      "Executing agent: {} with instruction: {}",
      execution.agentId(),
      execution.instruction()
    );

    // Call agent dynamically using ID
    var request = new AgentRequest(execution.instruction());
    DynamicMethodRef<AgentRequest, String> call = componentClient
      .forAgent()
      .inSession(sessionId())
      .dynamicCall(execution.agentId());

    String response = call.invoke(request);
    logger.info("Agent {} response: {}", execution.agentId(), response);

    var newState = currentState()
      .addMessage(execution.agentId() + ": " + response)
      .addAgentResponse(execution.agentId(), response);

    // Continue inner loop
    return stepEffects()
      .updateState(newState)
      .thenTransitionTo(AgentTeamWorkflow::evaluateProgressStep);
  }

  // ========== Replanning (Re-enter Outer Loop) ==========

  @StepName("update-ledger")
  private StepEffect updateLedgerStep() {
    logger.info(
      "REPLANNING: Updating task ledger (replan count: {})",
      currentState().replanCount()
    );

    // Update facts based on what we've learned
    var updatedFacts = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(LedgerAgent::process)
      .invoke(
        new LedgerAgent.UpdateFactsRequest(currentState().task(), currentState().facts())
      );

    logger.info("Updated facts:\n{}", updatedFacts);

    // Update plan based on what went wrong
    var teamDescription = getTeamDescription();
    var updatedPlan = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(LedgerAgent::process)
      .invoke(new LedgerAgent.UpdatePlanRequest(teamDescription));

    logger.info("Updated plan:\n{}", updatedPlan);

    // Update state with new plan and task ledger
    var ledgerMessage = buildTaskLedger(
      currentState().task(),
      teamDescription,
      updatedFacts,
      updatedPlan
    );

    // IMPORTANT: Preserve replanCount from current state, don't reset with init()
    var newState = currentState()
      .withFacts(updatedFacts)
      .withPlan(updatedPlan)
      .addMessage("UPDATED_TASK_LEDGER: " + ledgerMessage);

    // Re-enter inner loop - stallCount was already reset by startReplanning()
    return stepEffects()
      .updateState(newState)
      .thenTransitionTo(AgentTeamWorkflow::evaluateProgressStep);
  }

  // ========== Completion ==========

  @StepName("final-answer")
  private StepEffect finalAnswerStep() {
    logger.info("Generating final answer");

    var agentsAnswers = currentState().agentResponses().values();
    if (agentsAnswers.isEmpty()) {
      logger.warn("No agent responses to summarize");
      return stepEffects()
        .updateState(currentState().complete("Unable to generate an answer"))
        .thenPause();
    }

    var finalAnswer = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(SummarizerAgent::summarize)
      .invoke(new SummarizerAgent.Request(currentState().task(), agentsAnswers));

    logger.info("Final answer: {}", finalAnswer);

    return stepEffects().updateState(currentState().complete(finalAnswer)).thenPause();
  }

  @StepName("error")
  private StepEffect errorStep() {
    logger.error("Workflow error handling");
    return stepEffects()
      .updateState(currentState().fail("Workflow encountered an error"))
      .thenEnd();
  }

  // ========== Helper Methods ==========

  private String sessionId() {
    return commandContext().workflowId();
  }

  private String getTeamDescription() {
    var workers = agentRegistry.agentsWithRole("worker");
    var descriptions = workers
      .stream()
      .map(info -> info.id() + ": " + info.description().replace('\n', ' '))
      .toList();

    return String.join("\n\n", descriptions);
  }

  private String buildTaskLedger(String task, String team, String facts, String plan) {
    return """
    We are working to address the following user request:

    %s

    To answer this request we have assembled the following team:

    %s

    Here is the current fact sheet:

    %s

    Here is the plan to follow:

    %s
    """.stripIndent()
      .formatted(task, team, facts, plan);
  }
}
