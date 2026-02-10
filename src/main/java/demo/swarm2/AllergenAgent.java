package demo.swarm2;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
    id = "allergen-agent",
    name = "Allergen Agent",
    description = "Provides current allergen and pollen levels for specific locations and dates.")
public class AllergenAgent extends Agent {

  public Effect<String> query(String message) {
    return effects()
        .systemMessage("""
            You are an allergen monitoring agent. Provide pollen and allergen levels
            for the requested location and dates. Flag any days with dangerously high levels.""")
        .userMessage(message)
        .thenReply();
  }
}
