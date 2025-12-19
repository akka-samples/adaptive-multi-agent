package akka.javasdk.workflow.pattern;

/**
 * Minimal interface for workflow states that support the adaptive loop pattern.
 * <p>
 * Workflow states compose an {@link AdaptiveLoopState} instance and provide
 * accessors to get and update it. This keeps loop orchestration state separate
 * from workflow-specific state.
 *
 * @param <S> the concrete state type (for fluent returns)
 */
public interface WithAdaptiveLoopState<S extends WithAdaptiveLoopState<S>> {

  /**
   * Returns the adaptive loop state containing facts, plan, messages, and counters.
   */
  AdaptiveLoopState loopState();

  /**
   * Returns a new state with the updated loop state.
   *
   * @param loopState the new loop state
   * @return new state instance with updated loop state
   */
  S withLoopState(AdaptiveLoopState loopState);
}
