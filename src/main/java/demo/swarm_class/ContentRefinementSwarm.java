package demo.swarm_class;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.Swarm;
import demo.swarm6.ContentQuality;

import java.util.List;

/**
 * Iterative refinement swarm — a writer-critic loop. Equivalent to swarm6 using
 * the class-based design.
 *
 * <p>The orchestrator drives a feedback loop between a writer and a critic:
 * <ol>
 *   <li>Writer produces a draft</li>
 *   <li>Critic reviews it</li>
 *   <li>If not approved, orchestrator sends feedback back to the writer</li>
 *   <li>Repeat until the critic approves or maxTurns is reached</li>
 * </ol>
 */
@Component(id = "content-refinement")
public class ContentRefinementSwarm extends Swarm<String, ContentQuality> {

  @Override
  protected String instructions() {
    return """
        You coordinate a content creation and refinement process.

        Follow this loop:
        1. Hand off to the writer agent with the user's brief
        2. Hand off to the critic agent with the writer's output
        3. If the critic responds with APPROVED, you are done — compile the final result
        4. If the critic has feedback, hand off to the writer again with both
           the current content and the critic's feedback
        5. Repeat steps 2-4

        Track each revision cycle. When compiling the final result, include:
        - The final approved content
        - How many iterations were needed
        - A summary of each revision (what feedback was given, what changed)

        If you reach the turn limit without approval, use the best version so far.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent("writer-agent"),
        Handoff.toAgent("critic-agent"));
  }

  @Override
  protected Class<ContentQuality> resultType() {
    return ContentQuality.class;
  }

  @Override
  protected int maxTurns() {
    return 8;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
