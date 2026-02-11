package demo.swarm1;

import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.SwarmParams;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.Swarm;
import akka.javasdk.swarm.client.ComponentClient;

/**
 * Triage example
 *
 * In Akka, SpanishAgent and EnglishAgent are regular {@code @Component} agents.
 * The triage logic is expressed as a Swarm with handoffs — no custom triage agent class needed.
 */
public class TriageExample {

  private final ComponentClient componentClient;

  public TriageExample(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public String triage(String swarmId, String userMessage) {

    // Start the swarm — the LLM decides which agent to hand off to
    componentClient
        .forSwarm(swarmId)
        .method(Swarm::run)
        .invoke(SwarmParams.builder()
            .userMessage(userMessage)
            .instructions("Handoff to the appropriate agent based on the language of the request.")
            .handoffs(
                Handoff.toAgent("spanish-agent"),
                Handoff.toAgent("english-agent"))
            .maxTurns(3)
            .build());

    // Retrieve the result
    SwarmResult result = componentClient
        .forSwarm(swarmId)
        .method(Swarm::getResult)
        .invoke();

    if (result.isCompleted()) {
      return result.resultAs(String.class);
    } else {
      return "Swarm not completed: " + result.status().state();
    }
  }
}
