package akka.javasdk.workflow.pattern;

import akka.javasdk.annotations.StepName;
import akka.javasdk.workflow.Workflow;
import akka.japi.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for workflows implementing the sequential plan pattern.
 * <p>
 * Implements a linear execution flow:
 * <ol>
 *   <li>Create a sequential execution plan (including agent selection)</li>
 *   <li>Execute each step in the plan sequentially (or in parallel groups)</li>
 *   <li>Summarize all responses into a final answer</li>
 * </ol>
 * <p>
 * Key features:
 * <ul>
 *   <li>Queue-based execution: Steps execute in predetermined order</li>
 *   <li>Simple flow control: No adaptive evaluation or replanning</li>
 *   <li>Parallel execution: Execute groups of agents concurrently using virtual threads</li>
 *   <li>Configurable safety limits via maxSteps()</li>
 * </ul>
 * <p>
 * <b>Required Implementations:</b>
 * <ul>
 *   <li>{@link #createPlan()} - Create execution plan with sequential/parallel steps</li>
 *   <li>{@link #executeAgent(String, String)} - Execute a single agent and update state with response</li>
 *   <li>{@link #summarize()} - Generate final answer from agent responses</li>
 *   <li>{@link #handleFailure(String)} - Handle workflow failure</li>
 * </ul>
 * <p>
 * <b>Optional Overrides:</b>
 * <ul>
 *   <li>{@link #maxSteps()} - Maximum steps to execute before failing (default: 50)</li>
 * </ul>
 * <p>
 * <b>Usage:</b>
 * <ul>
 *   <li>State type must implement {@link WithSequentialPlanState} for state composition</li>
 *   <li>Use {@link #planStart()} in start command to begin the workflow</li>
 *   <li>Create plan using {@link PlanStep#of(String, String)} for sequential steps</li>
 *   <li>For parallel execution: Use {@link PlanStep#parallel(PlanStep.Sequential...)} to group steps.
 *       Agent calls execute concurrently on virtual threads, then state updates apply
 *       sequentially to avoid race conditions.</li>
 * </ul>
 *
 * @param <S> the workflow state type (must implement WithSequentialPlanState)
 * @param <W> the concrete workflow type (needed for type-safe step transitions)
 */
public abstract class SequentialPlanWorkflow<S extends WithSequentialPlanState<S>, W extends SequentialPlanWorkflow<S, W>> extends Workflow<S> {

  private static final Logger logger = LoggerFactory.getLogger(SequentialPlanWorkflow.class);

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
   *       return state.addResponse(agentId, response);
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

    // FIXME it's probably possible to make this non-static and thereby remove the S type parameter

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
      // FIXME the Builder can be improved

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
  private Function<W, StepEffect> step(akka.japi.function.Function<SequentialPlanWorkflow<S, W>, StepEffect> stepMethod) {
    return (Function<W, StepEffect>) (Object) stepMethod;
  }

  /**
   * Returns the entry point step reference for starting the sequential plan.
   * Use this in command handlers when transitioning to start the workflow.
   */
  protected final Function<W, StepEffect> planStart() {
    return step(w -> w.createPlanStep());
  }

  // ========== Configuration (override to customize) ==========

  /**
   * Maximum number of steps to execute before giving up.
   * Provides a safety limit to prevent infinite execution.
   */
  protected int maxSteps() {
    return 50;
  }

  // ========== Abstract Callbacks (must implement) ==========

  /**
   * Creates a sequential execution plan.
   * Called at the start of the workflow.
   * Should:
   * <ol>
   *   <li>Select which agents can handle the task</li>
   *   <li>Create sequential plan steps for those agents</li>
   *   <li>Return updated state with plan steps set</li>
   * </ol>
   * Application-specific data (selected agents, etc.) should be stored in
   * the concrete workflow state, not in planState().
   *
   * @return the updated state with plan steps
   */
  protected abstract S createPlan() throws Exception;

  /**
   * Executes a single agent.
   * Called by the framework for both sequential and parallel steps in the plan.
   * Should return an effect describing how to call the agent and update state with the response.
   * <p>
   * For parallel execution, multiple agent calls are executed concurrently on virtual threads,
   * then their state updates are applied sequentially to avoid concurrent modifications.
   * <p>
   * For sequential execution, the agent call is executed immediately and the state update
   * is applied right away.
   * <p>
   * Use the builder pattern to construct the effect:
   * <pre>{@code
   * return AgentExecutionEffect
   *     .call(() -> componentClient.forAgent().dynamicCall(agentId).invoke(request))
   *     .updateState((response, state) -> state.addResponse(agentId, response));
   * }</pre>
   *
   * @param agentId the agent to execute
   * @param instruction the instruction for the agent
   * @return an effect describing how to execute the agent and update state
   */
  protected abstract AgentExecutionEffect<?, S> executeAgent(
      String agentId, String instruction) throws Exception;

  /**
   * Summarizes all agent responses into a final answer.
   * Called after all plan steps have been executed.
   * Should generate final answer and return completed state.
   *
   * @return the updated state with final answer
   */
  protected abstract S summarize() throws Exception;

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
   * Create plan step - entry point for the workflow.
   */
  @StepName("create-plan")
  public StepEffect createPlanStep() throws Exception {
    logger.info("Creating execution plan");

    S newState = createPlan();
    int stepCount = newState.planState().remainingSteps().size();
    logger.info("Created plan with {} step(s)", stepCount);

    if (stepCount == 0) {
      logger.warn("Plan is empty - cannot proceed");
      return stepEffects()
          .updateState(handleFailure("Plan creation resulted in no steps"))
          .thenEnd();
    }

    if (stepCount > maxSteps()) {
      logger.warn("Plan has {} steps, exceeds max of {}", stepCount, maxSteps());
      return stepEffects()
          .updateState(handleFailure("Plan exceeds maximum allowed steps"))
          .thenEnd();
    }

    return stepEffects()
        .updateState(newState)
        .thenTransitionTo(step(w -> w.executePlanStep()));
  }

  /**
   * Execute plan step - executes steps from the plan queue.
   * Handles both sequential and parallel execution.
   */
  @StepName("execute-plan")
  public StepEffect executePlanStep() throws Exception {
    S state = currentState();
    PlanStep step = state.planState().nextStep();

    S newState = switch (step) {
      case PlanStep.Sequential seq -> {
        logger.info("Executing sequential step: agent={}", seq.agentId());
        var effect = executeAgent(seq.agentId(), seq.instruction());
        Object response = effect.executeCall();
        @SuppressWarnings("unchecked")
        S result = ((AgentExecutionEffect<Object, S>) effect).applyUpdate(response, state);
        logger.info("Sequential step completed");
        yield result;
      }
      case PlanStep.Parallel par -> {
        logger.info("Executing parallel group with {} step(s)", par.steps().size());

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

    // Remove the executed step from the queue
    S stateWithStepRemoved = newState.withPlanState(newState.planState().removeFirstStep());

    if (stateWithStepRemoved.planState().hasMoreSteps()) {
      int remaining = stateWithStepRemoved.planState().remainingSteps().size();
      logger.info("{} step(s) remaining", remaining);
      return stepEffects()
          .updateState(stateWithStepRemoved)
          .thenTransitionTo(step(w -> w.executePlanStep()));
    } else {
      logger.info("All steps executed - proceeding to summarize");
      return stepEffects()
          .updateState(stateWithStepRemoved)
          .thenTransitionTo(step(w -> w.summarizeStep()));
    }
  }

  /**
   * Summarize step - generates final answer and completes the workflow.
   */
  @StepName("summarize")
  public StepEffect summarizeStep() throws Exception {
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
}
