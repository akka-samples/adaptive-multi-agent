package demo.swarm4;

import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.PauseReason;
import akka.javasdk.swarm.Swarm;
import akka.javasdk.swarm.SwarmParams;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.client.ComponentClient;

/**
 * Insurance policy re-rating example — demonstrates human-in-the-loop (HITL).
 *
 * The swarm analyses policies and produces hypothetical re-ratings. If the APR change
 * exceeds 0.5%, the swarm pauses to await underwriter approval before completing.
 */
public class PolicyReRatingExample {

  private static final String INSTRUCTIONS = """
      Perform on-demand analysis of the insurance policies identified in the user input
      and reply with hypothetical changes to those policies based on the additional fields
      in user input.

      Use the records agent to retrieve current policy data and rating history.

      If the value of a customer's Annual Percentage Rate in the hypothetical is more than
      0.5% different than the current rating, you will send a notification to the underwriting
      team with a diff comparison of the old and new policies using the notifyUnderwriter tool.
      You will also pause the current plan and await external interaction from authorised personnel.

      Once approval is received, finalize the re-rated policy output.
      """;

  private final ComponentClient componentClient;
  private final UnderwriterNotifier underwriterNotifier = new UnderwriterNotifier();

  public PolicyReRatingExample(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public RatedPolicyOutput reRate(String sessionId, String userInput) {

    // Start the swarm — may pause if APR change exceeds threshold
    componentClient
        .forSwarm()
        .inSession(sessionId)
        .method(Swarm::run)
        .invoke(SwarmParams.builder()
            .userMessage(userInput)
            .instructions(INSTRUCTIONS)
            .responseAs(RatedPolicyOutput.class)
            .tools(underwriterNotifier)
            .handoffs(
                Handoff.toAgent("policy-records-agent")
                    .withDescription("Retrieves current policy data and rating history"))
            .maxTurns(10)
            .build());

    // Check result — may be paused awaiting underwriter approval
    SwarmResult result = componentClient
        .forSwarm()
        .inSession(sessionId)
        .method(Swarm::getResult)
        .invoke();

    if (result.isPaused()) {
      // Swarm is waiting for human approval.
      // An external process (e.g. underwriter UI) would later call:
      //   componentClient.forSwarm().inSession(sessionId)
      //       .method(Swarm::resume).invoke("Approved by underwriter Jane Doe");
      PauseReason reason = result.status().pauseReason().orElseThrow();
      throw new AwaitingApprovalException(reason.message(), sessionId);
    }

    if (result.isCompleted()) {
      return result.resultAs(RatedPolicyOutput.class);
    } else {
      throw new RuntimeException("Re-rating did not complete: " + result.status().state());
    }
  }

  public static class AwaitingApprovalException extends RuntimeException {
    private final String sessionId;

    public AwaitingApprovalException(String message, String sessionId) {
      super(message);
      this.sessionId = sessionId;
    }

    public String sessionId() {
      return sessionId;
    }
  }
}
