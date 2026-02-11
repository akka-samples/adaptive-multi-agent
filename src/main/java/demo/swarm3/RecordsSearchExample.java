package demo.swarm3;

import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.Swarm;
import akka.javasdk.swarm.SwarmParams;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.client.ComponentClient;

import java.util.List;

/**
 * UCC records search example — a swarm with dynamically discovered agents.
 * Demonstrates how the handoff list can be populated at runtime
 * (e.g. from an agent directory) rather than hardcoded.
 */
public class RecordsSearchExample {

  private static final String INSTRUCTIONS = """
      Take the user input and use the tools and handoffs available to you to perform
      a records search in all applicable data sources.

      Search across all available record databases for UCC filings matching the
      property address. Each handoff is a specialized search agent for a specific
      records database.

      Return the search result with the highest accuracy score.
      """;

  private final ComponentClient componentClient;

  public RecordsSearchExample(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public UCCSearchResult search(String swarmId, String address) {

    // Dynamically discover agents capable of searching public real estate records.
    // This could query an agent directory, a view, or be hardcoded.
    List<Handoff> searchAgents = discoverSearchAgents();

    String userInput = "Find all Uniform Commercial Code (UCC) filings for the property at "
        + "address " + address + " where the property has been declared as collateral on a loan";

    componentClient
        .forSwarm(swarmId)
        .method(Swarm::run)
        .invoke(SwarmParams.builder()
            .userMessage(userInput)
            .instructions(INSTRUCTIONS)
            .responseAs(UCCSearchResult.class)
            .handoffs(searchAgents.toArray(Handoff[]::new))
            .maxTurns(15)
            .build());

    SwarmResult result = componentClient
        .forSwarm(swarmId)
        .method(Swarm::getResult)
        .invoke();

    if (result.isCompleted()) {
      return result.resultAs(UCCSearchResult.class);
    } else {
      throw new RuntimeException("Search did not complete: " + result.status().state());
    }
  }

  /**
   * Discover agents with the capability to search public real estate records.
   * In a real system this might query a view or agent registry.
   */
  private List<Handoff> discoverSearchAgents() {
    return List.of(
        Handoff.toAgent("county-records-agent")
            .withDescription("Searches county clerk UCC filing databases"),
        Handoff.toAgent("state-sos-agent")
            .withDescription("Searches Secretary of State UCC filing records"),
        Handoff.toAgent("federal-records-agent")
            .withDescription("Searches federal lien and filing databases"));
  }
}
