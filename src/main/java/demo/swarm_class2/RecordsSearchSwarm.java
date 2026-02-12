package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class.SwarmParams;
import demo.swarm3.UCCSearchResult;

import java.util.List;

/**
 * UCC records search swarm — dynamically discovers search agents at runtime.
 *
 * <p>Demonstrates how {@link #parameters()} can use {@link #getInput()} to build
 * the user message dynamically, while the handoff list is populated from a
 * discovery mechanism (e.g. agent registry, view) rather than hardcoded.
 */
@Component(id = "records-search")
public class RecordsSearchSwarm extends Swarm<String, UCCSearchResult> {

  @Override
  protected SwarmParams parameters() {
    // Dynamically discover agents capable of searching public real estate records.
    // This could query an agent directory, a view, or be hardcoded.
    List<Handoff> searchAgents = discoverSearchAgents();

    return SwarmParams.builder()
        .instructions("""
            Take the user input and use the tools and handoffs available to you to perform
            a records search in all applicable data sources.

            Search across all available record databases for UCC filings matching the
            property address. Each handoff is a specialized search agent for a specific
            records database.

            Return the search result with the highest accuracy score.

            User request: Find all Uniform Commercial Code (UCC) filings for the property at
            address """ + getInput() + " where the property has been declared as collateral on a loan")
        .handoffs(searchAgents.toArray(Handoff[]::new))
        .maxTurns(15)
        .build();
  }

  @Override
  protected Class<UCCSearchResult> resultType() {
    return UCCSearchResult.class;
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

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
