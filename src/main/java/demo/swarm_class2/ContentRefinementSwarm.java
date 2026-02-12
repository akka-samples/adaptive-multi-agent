package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class.SwarmParams;
import demo.swarm6.ContentQuality;
import demo.swarm6.CriticAgent;
import demo.swarm6.WriterAgent;

/**
 * Iterative refinement swarm — a writer-critic loop.
 */
@Component(id = "content-refinement-v2")
public class ContentRefinementSwarm extends Swarm<String, ContentQuality> {

  @Override
  protected SwarmParams parameters() {
    return SwarmParams.builder()
        .instructions("""
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

            If you reach the turn limit without approval, use the best version so far.""")
        .handoffs(
            Handoff.toAgent(WriterAgent.class),
            Handoff.toAgent(CriticAgent.class))
        .maxTurns(8)
        .build();
  }

  @Override
  protected Class<ContentQuality> resultType() {
    return ContentQuality.class;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
