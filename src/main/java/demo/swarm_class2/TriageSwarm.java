package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class.SwarmParams;

/**
 * Triage swarm — routes to the appropriate agent based on the language of the request.
 *
 * <p>The triage logic is expressed entirely in the instructions. The orchestrator LLM
 * detects the language and hands off to the matching agent. No custom triage agent needed.
 */
@Component(id = "triage")
public class TriageSwarm extends Swarm<String, String> {

  @Override
  protected SwarmParams parameters() {
    return SwarmParams.builder()
        .instructions("Handoff to the appropriate agent based on the language of the request.")
        .handoffs(
            Handoff.toAgent("spanish-agent"),
            Handoff.toAgent("english-agent"))
        .maxTurns(3)
        .build();
  }

  @Override
  protected Class<String> resultType() {
    return String.class;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
