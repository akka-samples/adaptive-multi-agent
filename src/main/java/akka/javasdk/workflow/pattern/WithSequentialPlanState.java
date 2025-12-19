package akka.javasdk.workflow.pattern;

/**
 * Minimal interface for workflow states that support the sequential plan pattern.
 * <p>
 * Workflow states compose a {@link SequentialPlanState} instance and provide
 * accessors to get and update it. This keeps plan execution state separate
 * from workflow-specific state.
 * <p>
 * Example implementation:
 * <pre>{@code
 * public record MyWorkflowState(
 *     String task,
 *     SequentialPlanState planState,
 *     Status status
 * ) implements WithSequentialPlanState<MyWorkflowState> {
 *
 *   public static MyWorkflowState init(String task) {
 *     return new MyWorkflowState(task, SequentialPlanState.init(), Status.STARTED);
 *   }
 *
 *   @Override
 *   public SequentialPlanState planState() { return planState; }
 *
 *   @Override
 *   public MyWorkflowState withPlanState(SequentialPlanState planState) {
 *     return new MyWorkflowState(task, planState, status);
 *   }
 * }
 * }</pre>
 *
 * @param <S> the concrete state type (for fluent returns)
 */
public interface WithSequentialPlanState<S extends WithSequentialPlanState<S>> {

  /**
   * Returns the sequential plan state containing remaining steps, responses, and history.
   */
  SequentialPlanState planState();

  /**
   * Returns a new state with the updated plan state.
   *
   * @param planState the new plan state
   * @return new state instance with updated plan state
   */
  S withPlanState(SequentialPlanState planState);
}
