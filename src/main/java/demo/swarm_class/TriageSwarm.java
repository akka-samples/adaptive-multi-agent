package demo.swarm_class;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.Swarm;
import demo.swarm1.EnglishAgent;
import demo.swarm1.SpanishAgent;

import java.util.List;

/**
 * Triage swarm — routes to the appropriate agent based on the language of the request.
 *
 * <p>The triage logic is expressed entirely in the instructions. The orchestrator LLM
 * detects the language and hands off to the matching agent. No custom triage agent needed.
 */
@Component(id = "triage")
public class TriageSwarm extends Swarm<String, String> {

  @Override
  protected String instructions() {
    return "Handoff to the appropriate agent based on the language of the request.";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent(SpanishAgent.class),
        Handoff.toAgent(EnglishAgent.class));
  }

  @Override
  protected Class<String> resultType() {
    return String.class;
  }

  @Override
  protected int maxTurns() {
    return 3;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
