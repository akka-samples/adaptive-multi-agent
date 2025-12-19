package demo.multiagent.application;

import static demo.multiagent.domain.AgentTeamState.Status.COMPLETED;
import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import demo.multiagent.domain.AgentRequest;
import demo.multiagent.domain.AgentTeamState;
import akka.javasdk.workflow.pattern.AdaptiveLoopWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent orchestrator workflow implementing the adaptive loop pattern.
 * <p>
 * Extends AdaptiveLoopWorkflow which provides the step methods and orchestration logic.
 * This class only needs to implement the business logic callbacks.
 */
@Component(id = "agent-team")
public class AgentTeamWorkflow extends AdaptiveLoopWorkflow<AgentTeamState, AgentTeamWorkflow> {

  public record Request(String userId, String message, Double budgetLimit) {
    // Convenience constructor with default budget
    public Request(String userId, String message) {
      this(userId, message, null);
    }
  }

  public record ApprovalDecision(String approvalId, boolean approved) {}

  private static final Logger logger = LoggerFactory.getLogger(AgentTeamWorkflow.class);

  private final ComponentClient componentClient;
  private final AgentRegistry agentRegistry;

  public AgentTeamWorkflow(ComponentClient componentClient, AgentRegistry agentRegistry) {
    this.componentClient = componentClient;
    this.agentRegistry = agentRegistry;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
      .defaultStepTimeout(ofSeconds(60))
      .defaultStepRecovery(maxRetries(1).failoverTo(AgentTeamWorkflow::errorStep))
      .build();
  }

  // ========== Configuration ==========

  @Override
  protected int maxTurns() {
    return 15;
  }

  @Override
  protected int stallThreshold() {
    return 3;
  }

  @Override
  protected int maxReplans() {
    return 2;
  }

  // ========== Command Handlers ==========

  public Effect<Done> start(Request request) {
    if (currentState() == null) {
      var state = request.budgetLimit() != null
          ? AgentTeamState.init(request.message(), request.budgetLimit())
          : AgentTeamState.init(request.message());

      logger.info("Starting workflow with budget limit: ${}", state.budgetLimit());

      return effects()
        .updateState(state)
        .transitionTo(loopStart())
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
      var messages = currentState().loopState().messageHistory();
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

  // ========== Adaptive Loop Callbacks ==========

  @Override
  protected AgentTeamState gatherFacts() {
    logger.info("Gathering facts for task: {}", currentState().task());

    double cost = estimateAgentCost("ledger");
    String facts = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(LedgerAgent::process)
      .invoke(new LedgerAgent.GatherFactsRequest(currentState().task()));

    var updatedLoop = currentState().loopState()
        .withFacts(facts)
        .addMessage(String.format("COST: $%.2f for gatherFacts (total: $%.2f / $%.2f)",
            cost, currentState().currentSpent() + cost, currentState().budgetLimit()));

    return currentState().withLoopState(updatedLoop).addSpent(cost);
  }

  @Override
  protected AgentTeamState createPlan() {
    logger.info("Creating plan based on facts");
    var teamDescription = getTeamDescription();

    double cost = estimateAgentCost("ledger");
    String plan = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(LedgerAgent::process)
      .invoke(
        new LedgerAgent.CreatePlanRequest(
          currentState().task(),
          teamDescription,
          currentState().loopState().facts()
        )
      );

    // Add task ledger to message history (specific to AgentTeamWorkflow)
    var ledgerMessage = buildTaskLedger(
        currentState().task(),
        teamDescription,
        currentState().loopState().facts(),
        plan
    );

    var updatedLoop = currentState().loopState()
        .withPlan(plan)
        .addMessage("TASK_LEDGER: " + ledgerMessage)
        .addMessage(String.format("COST: $%.2f for createPlan (total: $%.2f / $%.2f)",
            cost, currentState().currentSpent() + cost, currentState().budgetLimit()));

    return currentState().withLoopState(updatedLoop).addSpent(cost);
  }

  @Override
  protected ProgressEvaluation evaluateProgress(int turn) {
    var workers = agentRegistry.agentsWithRole("worker");
    var participantIds = workers.stream().map(AgentRegistry.AgentInfo::id).toList();

    // If single agent team, skip orchestrator and use it directly
    if (participantIds.size() == 1) {
      var agentId = participantIds.getFirst();
      logger.info("Single agent team - executing {}", agentId);
      var instruction = "Address the task: " + currentState().task();

      // Check budget before execution
      double estimatedCost = estimateAgentCost(agentId);
      if (!currentState().hasRemainingBudget(estimatedCost)) {
        String context = String.format(
            "Budget approval needed:\n" +
            "Agent: %s\n" +
            "Estimated cost: $%.2f\n" +
            "Current spent: $%.2f of $%.2f\n" +
            "Would exceed budget by: $%.2f",
            agentId, estimatedCost,
            currentState().currentSpent(), currentState().budgetLimit(),
            (currentState().currentSpent() + estimatedCost) - currentState().budgetLimit()
        );
        return ProgressEvaluation.awaitingApproval(agentId, instruction, context);
      }

      return ProgressEvaluation.continueWith(agentId, instruction);
    }

    // Call orchestrator to evaluate progress
    // NOTE: orchestrator cost is tracked separately - see comment below
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
          turn
        )
      );

    logger.info(
      "Progress: satisfied={}, inLoop={}, makingProgress={}, nextSpeaker={}",
      evaluation.isRequestSatisfied().answer(),
      evaluation.isInLoop().answer(),
      evaluation.isProgressBeingMade().answer(),
      evaluation.nextSpeaker().answer()
    );

    // Check if request is satisfied
    if (evaluation.isRequestSatisfied().answer()) {
      return ProgressEvaluation.complete(evaluation.isRequestSatisfied().reason());
    }

    // Check for stalling
    boolean isStalled = !evaluation.isProgressBeingMade().answer() ||
                        evaluation.isInLoop().answer();

    String nextAgentId = evaluation.nextSpeaker().answer();
    String instruction = evaluation.instructionOrQuestion().answer();

    if (isStalled) {
      String reason = evaluation.isInLoop().answer()
          ? evaluation.isInLoop().reason()
          : evaluation.isProgressBeingMade().reason();
      return ProgressEvaluation.stalled(nextAgentId, instruction, reason);
    }

    // HITL: Check budget before executing next agent
    double estimatedCost = estimateAgentCost(nextAgentId);
    if (!currentState().hasRemainingBudget(estimatedCost)) {
      String context = String.format(
          "Budget approval needed:\n" +
          "Turn: %d\n" +
          "Agent: %s\n" +
          "Instruction: %s\n" +
          "Estimated cost: $%.2f\n" +
          "Current spent: $%.2f of $%.2f\n" +
          "Remaining budget: $%.2f\n" +
          "Would exceed budget by: $%.2f",
          turn, nextAgentId, instruction, estimatedCost,
          currentState().currentSpent(), currentState().budgetLimit(),
          currentState().remainingBudget(),
          (currentState().currentSpent() + estimatedCost) - currentState().budgetLimit()
      );
      return ProgressEvaluation.awaitingApproval(nextAgentId, instruction, context);
    }

    return ProgressEvaluation.continueWith(nextAgentId, instruction);
  }

  // NOTE: evaluateProgress() calls the orchestrator but doesn't track costs because
  // it returns ProgressEvaluation, not state. The orchestrator cost could be tracked
  // in innerLoopStep() if needed, but for simplicity we only track costs in methods
  // that directly return updated state (gatherFacts, createPlan, executeAgent, etc.)

  @Override
  protected AgentExecutionEffect<?, AgentTeamState> executeAgent(
      String agentId, String instruction) {
    // Capture cost for this agent
    final double agentCost = estimateAgentCost(agentId);

    return AgentExecutionEffect
        .call(() -> {
          logger.info("Calling agent {} with: {}", agentId, instruction);
          var request = new AgentRequest(instruction);
          DynamicMethodRef<AgentRequest, String> call = componentClient
              .forAgent()
              .inSession(sessionId())
              .dynamicCall(agentId);

          String response = call.invoke(request);
          logger.info("Agent {} completed (cost: ${})", agentId, agentCost);
          return response;
        })
        .updateState((response, state) -> {
          var updatedLoop = state.loopState()
              .addMessage(agentId + ": " + response)
              .addAgentResponse(agentId, response)
              .addMessage(String.format("COST: $%.2f (total: $%.2f / $%.2f)",
                  agentCost, state.currentSpent() + agentCost, state.budgetLimit()));
          return state.withLoopState(updatedLoop).addSpent(agentCost);
        });
  }

  @Override
  protected AgentTeamState summarize() {
    logger.info("Summarizing {} agent responses", currentState().loopState().agentResponses().size());

    var agentsAnswers = currentState().loopState().agentResponses().values();

    if (agentsAnswers.isEmpty()) {
      logger.warn("No agent responses to summarize");
      return currentState().complete("Unable to generate an answer");
    }

    double cost = estimateAgentCost("summarizer");
    String finalAnswer = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(SummarizerAgent::summarize)
      .invoke(new SummarizerAgent.Request(currentState().task(), agentsAnswers));

    // Track cost before completing
    var stateWithCost = currentState()
        .withLoopState(currentState().loopState()
            .addMessage(String.format("COST: $%.2f for summarize (total: $%.2f / $%.2f)",
                cost, currentState().currentSpent() + cost, currentState().budgetLimit())))
        .addSpent(cost);

    return stateWithCost.complete(finalAnswer);
  }

  @Override
  protected AgentTeamState updateFacts() {
    logger.info("Updating facts during replan");

    double cost = estimateAgentCost("ledger");
    String facts = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(LedgerAgent::process)
      .invoke(
        new LedgerAgent.UpdateFactsRequest(currentState().task(), currentState().loopState().facts())
      );

    var updatedLoop = currentState().loopState()
        .withFacts(facts)
        .addMessage(String.format("COST: $%.2f for updateFacts (total: $%.2f / $%.2f)",
            cost, currentState().currentSpent() + cost, currentState().budgetLimit()));

    return currentState().withLoopState(updatedLoop).addSpent(cost);
  }

  @Override
  protected AgentTeamState handleFailure(String reason) {
    return currentState().fail(reason);
  }

  @Override
  protected AgentTeamState updatePlan() {
    logger.info("Updating plan during replan");

    // First update facts (already tracks its own cost)
    AgentTeamState stateWithFacts = updateFacts();

    // Then update plan based on new facts
    double cost = estimateAgentCost("ledger");
    var teamDescription = getTeamDescription();
    String plan = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(LedgerAgent::process)
      .invoke(new LedgerAgent.UpdatePlanRequest(teamDescription));

    // Add updated task ledger
    var ledgerMessage = buildTaskLedger(
        stateWithFacts.task(),
        teamDescription,
        stateWithFacts.loopState().facts(),
        plan
    );

    var updatedLoop = stateWithFacts.loopState()
        .withPlan(plan)
        .addMessage("UPDATED_TASK_LEDGER: " + ledgerMessage)
        .addMessage(String.format("COST: $%.2f for updatePlan (total: $%.2f / $%.2f)",
            cost, stateWithFacts.currentSpent() + cost, stateWithFacts.budgetLimit()));

    return stateWithFacts.withLoopState(updatedLoop).addSpent(cost);
  }

  // ========== HITL Command Handlers ==========

  /**
   * Approve or reject a budget-exceeding operation.
   */
  public Effect<Done> approve(ApprovalDecision decision) {
    return handleApproval(decision.approvalId(), decision.approved());
  }

  /**
   * Get pending approval details.
   */
  public ReadOnlyEffect<String> getPendingApprovalContext() {
    var pendingApproval = currentState().loopState().pendingApproval();
    if (pendingApproval == null) {
      return effects().reply("No pending approval");
    }
    return effects().reply(pendingApproval.evaluation().approvalContext());
  }

  /**
   * Get budget status.
   */
  public ReadOnlyEffect<BudgetStatus> getBudgetStatus() {
    if (currentState() == null) {
      return effects().error("Workflow not started");
    }
    return effects().reply(new BudgetStatus(
        currentState().budgetLimit(),
        currentState().currentSpent(),
        currentState().remainingBudget()
    ));
  }

  public record BudgetStatus(double budgetLimit, double currentSpent, double remaining) {}

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

  /**
   * Estimates the cost of calling a specific agent.
   * In a real system, this would be based on actual API costs, token counts, etc.
   */
  private double estimateAgentCost(String agentId) {
    // Simulate different costs for different agent types
    if (agentId.contains("orchestrator")) {
      return 0.50;  // Orchestrator uses expensive LLM
    } else if (agentId.contains("web-searcher")) {
      return 1.00;  // Web search API costs
    } else if (agentId.contains("code-executor")) {
      return 0.75;  // Code execution environment costs
    } else if (agentId.contains("summarizer")) {
      return 0.30;  // Summarization is cheaper
    } else if (agentId.contains("ledger")) {
      return 0.40;  // Ledger operations
    } else {
      return 0.25;  // Default cost for other agents
    }
  }
}
