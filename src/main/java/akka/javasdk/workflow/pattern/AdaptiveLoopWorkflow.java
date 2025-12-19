package akka.javasdk.workflow.pattern;

import akka.javasdk.annotations.StepName;
import akka.javasdk.workflow.Workflow;
import akka.japi.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for workflows implementing the adaptive loop pattern.
 * <p>
 * Implements a two-phase loop pattern:
 * <ul>
 *   <li><b>Outer loop (Planning)</b>: Gather facts → Create plan → Enter inner loop</li>
 *   <li><b>Inner loop (Execution)</b>: Evaluate progress → Execute agent → Loop/Replan/Finish</li>
 * </ul>
 * <p>
 * Key features:
 * <ul>
 *   <li>Adaptive: Re-evaluates progress after each agent response</li>
 *   <li>Stall detection: Detects when agents are stuck or not making progress</li>
 *   <li>Replanning: Automatically re-enters planning phase when stalled</li>
 *   <li>Configurable thresholds for max turns, stalls, and replans</li>
 *   <li>Parallel execution: Execute multiple agents concurrently using virtual threads</li>
 *   <li>HITL support: Can pause for human approval via ProgressEvaluation.awaitingApproval()</li>
 * </ul>
 * <p>
 * <b>Required Implementations:</b>
 * <ul>
 *   <li>{@link #gatherFacts()} - Collect initial facts for planning</li>
 *   <li>{@link #createPlan()} - Create execution plan based on facts</li>
 *   <li>{@link #evaluateProgress(int)} - Evaluate progress and decide next action (complete/continue/stalled/awaitingApproval)</li>
 *   <li>{@link #executeAgent(String, String)} - Execute a single agent and update state with response</li>
 *   <li>{@link #summarize()} - Generate final answer from agent responses</li>
 *   <li>{@link #handleFailure(String)} - Handle workflow failure</li>
 * </ul>
 * <p>
 * <b>Optional Overrides:</b>
 * <ul>
 *   <li>{@link #maxTurns()} - Maximum turns in inner loop (default: 15)</li>
 *   <li>{@link #stallThreshold()} - Consecutive stalls before replanning (default: 3)</li>
 *   <li>{@link #maxReplans()} - Maximum replan attempts (default: 2)</li>
 *   <li>{@link #updateFacts()} - Update facts during replanning (default: calls gatherFacts())</li>
 *   <li>{@link #updatePlan()} - Update plan during replanning (default: calls createPlan())</li>
 * </ul>
 * <p>
 * <b>Usage:</b>
 * <ul>
 *   <li>State type must implement {@link WithAdaptiveLoopState} for state composition</li>
 *   <li>Use {@link #loopStart()} in start command to begin the workflow</li>
 *   <li>Use {@link #handleApproval(String, boolean)} for HITL approval handlers</li>
 *   <li>For parallel execution: Return {@link PlanStep.Parallel} from evaluateProgress() via
 *       {@link ProgressEvaluation#continueWith(PlanStep)}. Agent calls execute concurrently
 *       on virtual threads, then state updates apply sequentially to avoid race conditions.</li>
 * </ul>
 *
 * @param <S> the workflow state type (must implement WithAdaptiveLoopState)
 * @param <W> the concrete workflow type (needed for type-safe step transitions)
 */
public abstract class AdaptiveLoopWorkflow<S extends WithAdaptiveLoopState<S>, W extends AdaptiveLoopWorkflow<S, W>> extends Workflow<S> {

  private static final Logger logger = LoggerFactory.getLogger(AdaptiveLoopWorkflow.class);

  /**
   * Effect describing how to execute an agent and update state with its response.
   * <p>
   * Uses a builder pattern to separate the agent call from the state update:
   * <pre>{@code
   * return AgentExecutionEffect
   *     .call(() -> {
   *       // Call agent and return response
   *       return componentClient.forAgent()...invoke(request);
   *     })
   *     .updateState((response, state) -> {
   *       // Update state with response
   *       return state.addAgentResponse(agentId, response);
   *     });
   * }</pre>
   * <p>
   * The framework executes calls in parallel (for parallel steps) using virtual threads,
   * then applies state updates sequentially to avoid concurrent modifications.
   *
   * @param <R> the response type from the agent call
   */
  public static final class AgentExecutionEffect<R, S> {
    private final java.util.function.Supplier<R> call;
    private final java.util.function.BiFunction<R, S, S> updateState;

    private AgentExecutionEffect(
        java.util.function.Supplier<R> call,
        java.util.function.BiFunction<R, S, S> updateState) {
      this.call = call;
      this.updateState = updateState;
    }

    /**
     * Starts building an effect by specifying the agent call.
     * The call will be executed on a virtual thread and may run in parallel with other calls.
     *
     * @param call supplier that executes the agent and returns the response
     * @param <R> the response type
     * @return a builder to specify how to update state with the response
     */
    public static <R> Builder<R> call(java.util.function.Supplier<R> call) {
      return new Builder<>(call);
    }

    /**
     * Executes the agent call.
     * Called by the framework - applications should not call this directly.
     */
    public R executeCall() {
      return call.get();
    }

    /**
     * Applies the state update with the agent response.
     * Called by the framework - applications should not call this directly.
     */
    public S applyUpdate(R response, S state) {
      return updateState.apply(response, state);
    }

    /**
     * Builder for constructing AgentExecutionEffect instances.
     */
    public static final class Builder<R> {
      private final java.util.function.Supplier<R> call;

      private Builder(java.util.function.Supplier<R> call) {
        this.call = call;
      }

      /**
       * Specifies how to update state with the agent response.
       *
       * @param updateState function that takes the response and current state, returns updated state
       * @param <S> the state type
       * @return the complete effect
       */
      public <S> AgentExecutionEffect<R, S> updateState(java.util.function.BiFunction<R, S, S> updateState) {
        return new AgentExecutionEffect<>(call, updateState);
      }
    }
  }

  /**
   * Helper to create a step reference from a method on this workflow.
   * Uses unchecked cast to convert from base type to concrete workflow type.
   * This is safe because at runtime, W is always the actual workflow instance type.
   */
  @SuppressWarnings("unchecked")
  private Function<W, StepEffect> step(akka.japi.function.Function<AdaptiveLoopWorkflow<S, W>, StepEffect> stepMethod) {
    return (Function<W, StepEffect>) (Object) stepMethod;
  }

  /**
   * Returns the entry point step reference for starting the adaptive loop.
   * Use this in command handlers when transitioning to start the loop.
   */
  protected final Function<W, StepEffect> loopStart() {
    return step(w -> w.gatherFactsStep());
  }

  /**
   * Result of progress evaluation from the orchestrator.
   * Supports both single agent execution and parallel execution of multiple agents.
   */
  public record ProgressEvaluation(
      boolean isComplete,
      boolean isStalled,
      boolean isAwaitingApproval,
      String nextAgentId,
      String instruction,
      PlanStep nextStep,
      String reason,
      String approvalContext
  ) {
    public static ProgressEvaluation complete(String reason) {
      return new ProgressEvaluation(true, false, false, null, null, null, reason, null);
    }

    public static ProgressEvaluation continueWith(String agentId, String instruction) {
      return new ProgressEvaluation(false, false, false, agentId, instruction, null, null, null);
    }

    public static ProgressEvaluation continueWith(PlanStep step) {
      return new ProgressEvaluation(false, false, false, null, null, step, null, null);
    }

    public static ProgressEvaluation stalled(String agentId, String instruction, String reason) {
      return new ProgressEvaluation(false, true, false, agentId, instruction, null, reason, null);
    }

    /**
     * Indicates that the workflow should pause and await human approval before proceeding.
     * The workflow will pause with the provided context for the human operator.
     *
     * @param nextAgentId the agent to execute after approval
     * @param instruction the instruction for the agent after approval
     * @param approvalContext context/reason for requesting human approval
     * @return evaluation indicating HITL pause needed
     */
    public static ProgressEvaluation awaitingApproval(
        String nextAgentId, String instruction, String approvalContext) {
      return new ProgressEvaluation(false, false, true, nextAgentId, instruction, null, null, approvalContext);
    }

    /**
     * Variant that pauses before completion to allow human review of final answer.
     *
     * @param approvalContext context for the approval request (e.g., summary of results)
     * @return evaluation indicating completion approval needed
     */
    public static ProgressEvaluation awaitingCompletionApproval(String approvalContext) {
      return new ProgressEvaluation(false, false, true, null, null, null, null, approvalContext);
    }

    public boolean hasParallelExecution() {
      return nextStep != null;
    }

    public boolean isCompletionApproval() {
      return isAwaitingApproval && nextAgentId == null && nextStep == null;
    }
  }

  // ========== Configuration (override to customize) ==========

  /**
   * Maximum number of turns in the inner loop before giving up.
   */
  protected int maxTurns() {
    return 15;
  }

  /**
   * Number of consecutive stalls before triggering a replan.
   */
  protected int stallThreshold() {
    return 3;
  }

  /**
   * Maximum number of replan attempts before giving up.
   */
  protected int maxReplans() {
    return 2;
  }

  // ========== Abstract Callbacks (must implement) ==========

  /**
   * Gathers initial facts about the task.
   * Called at the start of the outer loop.
   * Should retrieve facts and return updated state with facts set.
   *
   * @return the updated state with facts
   */
  protected abstract S gatherFacts() throws Exception;

  /**
   * Creates a plan based on the gathered facts.
   * Called after gatherFacts in the outer loop.
   * Should create a plan and return updated state with plan set.
   * Can access currentState() to get facts.
   *
   * @return the updated state with plan (and any other workflow-specific updates)
   */
  protected abstract S createPlan() throws Exception;

  /**
   * Evaluates progress and decides the next action.
   * Called at the start of each inner loop iteration.
   *
   * @param turn the current turn number (1-indexed)
   * @return evaluation result indicating complete, continue, or stalled
   */
  protected abstract ProgressEvaluation evaluateProgress(int turn) throws Exception;

  /**
   * Executes a single agent.
   * Called by the framework for both sequential and parallel agent execution.
   * Should return an effect describing how to call the agent and update state with the response.
   * <p>
   * For parallel execution, multiple agent calls are executed concurrently on virtual threads,
   * then their state updates are applied sequentially to avoid concurrent modifications.
   * <p>
   * For sequential execution, the agent call is executed immediately and the state update
   * is applied right away.
   * <p>
   * The framework will automatically clear the pending execution after the agent completes.
   * <p>
   * Use the builder pattern to construct the effect:
   * <pre>{@code
   * return AgentExecutionEffect
   *     .call(() -> componentClient.forAgent().dynamicCall(agentId).invoke(request))
   *     .updateState((response, state) -> state.addAgentResponse(agentId, response));
   * }</pre>
   *
   * @param agentId the agent to execute
   * @param instruction the instruction for the agent
   * @return an effect describing how to execute the agent and update state
   */
  protected abstract AgentExecutionEffect<?, S> executeAgent(
      String agentId, String instruction) throws Exception;

  /**
   * Summarizes the agent responses into a final answer.
   * Called when the task is complete.
   * Should generate final answer and return completed state.
   * Should handle the case where there are no agent responses.
   *
   * @return the updated state with final answer
   */
  protected abstract S summarize() throws Exception;

  // ========== Optional Callbacks (override to customize replanning) ==========

  /**
   * Updates facts during replanning.
   * Default implementation calls gatherFacts().
   *
   * @return the updated state with new facts
   */
  protected S updateFacts() throws Exception {
    return gatherFacts();
  }

  /**
   * Updates the plan during replanning.
   * Default implementation calls createPlan().
   *
   * @return the updated state with new plan
   */
  protected S updatePlan() throws Exception {
    return createPlan();
  }

  /**
   * Handles workflow failure by returning a failed state.
   * Implementations should update workflow-specific status and add the failure message.
   *
   * @param reason the failure reason
   * @return the updated state marked as failed
   */
  protected abstract S handleFailure(String reason);

  // ========== Step Methods (implemented in base class) ==========

  /**
   * Gather facts step - entry point for the outer loop.
   */
  @StepName("gather-facts")
  public StepEffect gatherFactsStep() throws Exception {
    logger.info("OUTER LOOP: Gathering facts for task");

    S newState = gatherFacts();
    logger.info("Gathered facts:\n{}", newState.loopState().facts());

    return stepEffects()
        .updateState(newState)
        .thenTransitionTo(step(w -> w.createPlanStep()));
  }

  /**
   * Create plan step - creates a plan based on gathered facts.
   */
  @StepName("create-plan")
  public StepEffect createPlanStep() throws Exception {
    logger.info("OUTER LOOP: Creating plan");

    S newState = createPlan();
    logger.info("Created plan:\n{}", newState.loopState().plan());

    return stepEffects()
        .updateState(newState)
        .thenTransitionTo(step(w -> w.innerLoopStep()));
  }

  /**
   * Inner loop step - evaluates progress and decides next action.
   */
  @StepName("inner-loop")
  public StepEffect innerLoopStep() throws Exception {
    S state = currentState();
    // Increment turn at the start of each iteration (turns are 1-indexed)
    S stateWithTurn = state.withLoopState(state.loopState().incrementTurnCount());
    int turn = stateWithTurn.loopState().turnCount();
    logger.info("INNER LOOP: Turn {} of {}", turn, maxTurns());

    // Check max turns
    if (turn > maxTurns()) {
      logger.warn("Max turns ({}) reached", maxTurns());
      return stepEffects()
          .updateState(handleFailure("Maximum turns reached"))
          .thenTransitionTo(step(w -> w.completeStep()));
    }

    // Evaluate progress
    ProgressEvaluation eval = evaluateProgress(turn);

    // HITL: Check if awaiting human approval
    if (eval.isAwaitingApproval()) {
      logger.info("Awaiting human approval: {}", eval.approvalContext());
      var loopWithApproval = stateWithTurn.loopState()
          .addMessage("HITL_REQUEST: " + eval.approvalContext())
          .withPendingApproval(eval);

      return stepEffects()
          .updateState(stateWithTurn.withLoopState(loopWithApproval))
          .thenPause();
    }

    // Task complete?
    if (eval.isComplete()) {
      logger.info("Task complete: {}", eval.reason());
      var completeLoop = stateWithTurn.loopState().addMessage("COMPLETE: " + eval.reason());
      return stepEffects()
          .updateState(stateWithTurn.withLoopState(completeLoop))
          .thenTransitionTo(step(w -> w.completeStep()));
    }

    // Track stalling
    S newState = stateWithTurn;
    if (eval.isStalled()) {
      newState = newState.withLoopState(newState.loopState().incrementStallCount());
      logger.warn("Stall detected (count: {}): {}", newState.loopState().stallCount(), eval.reason());

      // Check if we need to replan
      if (newState.loopState().stallCount() >= stallThreshold()) {
        if (newState.loopState().replanCount() >= maxReplans()) {
          logger.warn("Max replans ({}) reached - giving up", maxReplans());
          return stepEffects()
              .updateState(handleFailure("Maximum replanning attempts reached"))
              .thenTransitionTo(step(w -> w.completeStep()));
        }

        logger.info("Stall threshold reached - triggering replan");
        return stepEffects()
            .updateState(newState.withLoopState(newState.loopState().startReplanning()))
            .thenTransitionTo(step(w -> w.replanStep()));
      }
    } else {
      newState = newState.withLoopState(newState.loopState().resetStallCount());
    }

    // Continue with next agent(s) - store pending execution in state
    if (eval.hasParallelExecution()) {
      logger.info("Next step: parallel/sequential execution");
      var loopWithMessage = newState.loopState()
          .addMessage("ORCHESTRATOR: Execute next step")
          .withPendingExecution(eval.nextStep());

      S continueState = newState.withLoopState(loopWithMessage);

      return stepEffects()
          .updateState(continueState)
          .thenTransitionTo(step(w -> w.executeAgentStep()));
    } else {
      logger.info("Next agent: {} - {}", eval.nextAgentId(), eval.instruction());

      var loopWithMessage = newState.loopState()
          .addMessage("ORCHESTRATOR: " + eval.instruction())
          .withPendingExecution(eval.nextAgentId(), eval.instruction());

      S continueState = newState.withLoopState(loopWithMessage);

      return stepEffects()
          .updateState(continueState)
          .thenTransitionTo(step(w -> w.executeAgentStep()));
    }
  }

  /**
   * Execute agent step - executes the pending agent(s) and continues the loop.
   * Handles both sequential and parallel execution.
   */
  @StepName("execute-agent")
  public StepEffect executeAgentStep() throws Exception {
    S state = currentState();
    var pending = state.loopState().pendingExecution();

    if (pending == null) {
      logger.error("No pending execution found in state");
      return stepEffects()
          .updateState(handleFailure("Internal error: no pending execution"))
          .thenTransitionTo(step(w -> w.errorStep()));
    }

    PlanStep step = pending.step();

    S newState = switch (step) {
      case PlanStep.Sequential seq -> {
        logger.info("Executing agent: {} with instruction: {}", seq.agentId(), seq.instruction());
        var effect = executeAgent(seq.agentId(), seq.instruction());
        Object response = effect.executeCall();
        @SuppressWarnings("unchecked")
        S result = ((AgentExecutionEffect<Object, S>) effect).applyUpdate(response, state);
        logger.info("Agent {} response captured", seq.agentId());
        yield result;
      }
      case PlanStep.Parallel par -> {
        logger.info("Executing parallel group with {} agent(s)", par.steps().size());

        // Collect effects for all parallel steps
        var effects = new java.util.ArrayList<AgentExecutionEffect<?, S>>();
        var agentIds = new java.util.ArrayList<String>();

        for (var seq : par.steps()) {
          try {
            logger.info("  Preparing execution: agent={}", seq.agentId());
            effects.add(executeAgent(seq.agentId(), seq.instruction()));
            agentIds.add(seq.agentId());
          } catch (Exception e) {
            throw new RuntimeException("Failed to create effect for agent " + seq.agentId(), e);
          }
        }

        // Execute all calls in parallel on virtual threads
        var futures = new java.util.ArrayList<CompletableFuture<?>>();
        for (int i = 0; i < effects.size(); i++) {
          final int index = i;
          futures.add(CompletableFuture.supplyAsync(() -> {
            logger.info("  Executing call: agent={}", agentIds.get(index));
            return effects.get(index).executeCall();
          }));
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Apply state updates sequentially to avoid concurrent modifications
        S result = state;
        for (int i = 0; i < effects.size(); i++) {
          Object response = futures.get(i).join();
          @SuppressWarnings("unchecked")
          var effect = (AgentExecutionEffect<Object, S>) effects.get(i);
          result = effect.applyUpdate(response, result);
          logger.info("  Applied state update: agent={}", agentIds.get(i));
        }

        logger.info("Parallel group completed");
        yield result;
      }
    };

    // Clear pending execution (framework manages this)
    S stateWithPendingCleared = newState.withLoopState(newState.loopState().clearPendingExecution());

    return stepEffects()
        .updateState(stateWithPendingCleared)
        .thenTransitionTo(step(w -> w.innerLoopStep()));
  }

  /**
   * Replan step - updates facts and plan, then re-enters inner loop.
   */
  @StepName("replan")
  public StepEffect replanStep() throws Exception {
    S state = currentState();
    logger.info("REPLANNING: Attempt {} of {}", state.loopState().replanCount(), maxReplans());

    // Update facts and plan
    S newState = updatePlan();
    logger.info("Updated facts and plan");

    return stepEffects()
        .updateState(newState)
        .thenTransitionTo(step(w -> w.innerLoopStep()));
  }

  /**
   * Complete step - summarizes responses and pauses the workflow.
   */
  @StepName("complete")
  public StepEffect completeStep() throws Exception {
    logger.info("Generating final answer");
    S newState = summarize();
    logger.info("Final answer generated");

    return stepEffects()
        .updateState(newState)
        .thenPause();
  }

  /**
   * Error step - handles workflow errors.
   */
  @StepName("error")
  public StepEffect errorStep() {
    logger.error("Workflow error");
    return stepEffects()
        .updateState(handleFailure("Workflow encountered an error"))
        .thenEnd();
  }

  // ========== HITL Support ==========

  /**
   * Helper to access the loop start step reference for HITL approval handlers.
   * Use this when you want to resume from the beginning of the inner loop after approval.
   *
   * @return step reference to inner loop
   */
  protected final Function<W, StepEffect> loopContinue() {
    return step(w -> w.innerLoopStep());
  }

  /**
   * Handles approval/rejection for HITL pause.
   * Validates the approval ID, updates state, and transitions to next step.
   * <p>
   * If approved: Sets up pending execution and transitions to executeAgentStep to execute the approved action.
   * If rejected: Fails the workflow and transitions to completeStep.
   * <p>
   * Example usage in subclass:
   * <pre>{@code
   * public record ApprovalDecision(String approvalId, boolean approved) {}
   *
   * public Effect<Done> approve(ApprovalDecision decision) {
   *   return handleApproval(decision.approvalId(), decision.approved());
   * }
   * }</pre>
   *
   * @param approvalId the approval ID from the request (must match pending approval)
   * @param approved whether the approval was granted
   * @return effect that updates state and transitions to next step
   */
  protected final Effect<akka.Done> handleApproval(String approvalId, boolean approved) {
    S state = currentState();
    if (state == null) {
      return effects().error("Workflow not initialized");
    }

    var pendingApproval = state.loopState().pendingApproval();
    if (pendingApproval == null) {
      return effects().error("No pending approval");
    }

    if (!pendingApproval.approvalId().equals(approvalId)) {
      return effects().error("Approval ID mismatch");
    }

    var evaluation = pendingApproval.evaluation();

    if (approved) {
      logger.info("HITL approval granted for: {}", evaluation.approvalContext());

      var loopWithMessage = state.loopState()
          .addMessage("HITL_APPROVED: " + evaluation.approvalContext())
          .clearPendingApproval();

      // Check if this is completion approval (no next agent to execute)
      if (evaluation.isCompletionApproval()) {
        return effects()
            .updateState(state.withLoopState(loopWithMessage))
            .transitionTo(step(w -> w.completeStep()))
            .thenReply(akka.Done.getInstance());
      }

      // Regular approval - set up pending execution
      if (evaluation.hasParallelExecution()) {
        loopWithMessage = loopWithMessage.withPendingExecution(evaluation.nextStep());
      } else {
        loopWithMessage = loopWithMessage.withPendingExecution(
            evaluation.nextAgentId(), evaluation.instruction());
      }

      return effects()
          .updateState(state.withLoopState(loopWithMessage))
          .transitionTo(step(w -> w.executeAgentStep()))
          .thenReply(akka.Done.getInstance());

    } else {
      logger.info("HITL approval rejected for: {}", evaluation.approvalContext());

      return effects()
          .updateState(handleFailure("Rejected by human: " + evaluation.approvalContext()))
          .transitionTo(step(w -> w.completeStep()))
          .thenReply(akka.Done.getInstance());
    }
  }
}
