package demo.swarm6;

import java.util.List;

/**
 * Structured response from the content refinement swarm.
 * The swarm terminates when the critic is satisfied and the orchestrator
 * produces output conforming to this type.
 */
public record ContentQuality(
    String finalContent,
    int iterationsNeeded,
    List<Revision> revisionHistory) {

  public record Revision(
      int iteration,
      String feedback,
      String changesApplied) {}
}
