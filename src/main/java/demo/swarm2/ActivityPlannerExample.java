package demo.swarm2;

import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.Swarm;
import akka.javasdk.swarm.SwarmParams;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.client.ComponentClient;

/**
 * Activity planner example — a swarm that gathers weather, calendar, and allergen data
 * to recommend outdoor activities. Demonstrates typed response termination.
 */
public class ActivityPlannerExample {

  private static final String INSTRUCTIONS = """
      Using the available tools and handoffs, gather information based on the user's
      desired activity specified in the input.

      Steps:
      1. Check the weather forecast for the requested dates and location
      2. Check the user's calendar availability
      3. Check current allergen levels for the location

      Recommend activities that are safe and enjoyable.
      If the allergen level on a target day is too high, that day should not be recommended.
      Only recommend activities on days when the user is available according to their calendar.
      """;

  private final ComponentClient componentClient;

  public ActivityPlannerExample(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public ActivityRecommendation plan(String swarmId, String userInput) {

    // Start the swarm with typed response — terminates when LLM conforms to ActivityRecommendation
    componentClient
        .forSwarm(swarmId)
        .method(Swarm::run)
        .invoke(SwarmParams.builder()
            .userMessage(userInput)
            .instructions(INSTRUCTIONS)
            .responseAs(ActivityRecommendation.class)
            .handoffs(
                Handoff.toAgent("weather-agent"),
                Handoff.toAgent("calendar-agent"),
                Handoff.toAgent("allergen-agent"))
            .maxTurns(10)
            .build());

    // Retrieve the typed result
    SwarmResult result = componentClient
        .forSwarm(swarmId)
        .method(Swarm::getResult)
        .invoke();

    if (result.isCompleted()) {
      return result.resultAs(ActivityRecommendation.class);
    } else {
      throw new RuntimeException("Swarm did not complete: " + result.status().state());
    }
  }
}
