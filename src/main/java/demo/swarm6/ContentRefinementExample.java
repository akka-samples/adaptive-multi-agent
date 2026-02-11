package demo.swarm6;

import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.Swarm;
import akka.javasdk.swarm.SwarmParams;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.client.ComponentClient;

/**
 * Iterative refinement example — a writer-critic loop.
 *
 * The swarm orchestrator drives a feedback loop between a writer and a critic:
 *   1. Writer produces a draft
 *   2. Critic reviews it
 *   3. If not approved, orchestrator sends feedback back to the writer
 *   4. Repeat until the critic approves or maxTurns is reached
 *
 * This demonstrates the core value of the swarm loop — the number of iterations
 * isn't known upfront. The LLM decides when quality is sufficient.
 */
public class ContentRefinementExample {

  private static final String INSTRUCTIONS = """
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

  private final ComponentClient componentClient;

  public ContentRefinementExample(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public ContentQuality createContent(String swarmId, String brief) {

    componentClient
        .forSwarm(swarmId)
        .method(Swarm::run)
        .invoke(SwarmParams.builder()
            .userMessage(brief)
            .instructions(INSTRUCTIONS)
            .responseAs(ContentQuality.class)
            .handoffs(
                Handoff.toAgent("writer-agent"),
                Handoff.toAgent("critic-agent"))
            .maxTurns(8)
            .build());

    return switch (componentClient.forSwarm(swarmId).method(Swarm::getResult).invoke()) {
      case SwarmResult.Completed c -> c.resultAs(ContentQuality.class);
      case SwarmResult.Failed f -> throw new RuntimeException("Refinement failed: " + f.reason());
      default -> throw new RuntimeException("Content refinement did not complete");
    };
  }
}
