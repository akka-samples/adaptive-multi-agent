package demo.swarm_class;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.Swarm;
import demo.swarm4.RatedPolicyOutput;
import demo.swarm4.UnderwriterNotifier;

import java.util.List;

/**
 * Insurance policy re-rating swarm with HITL — equivalent to swarm4 using the class-based design.
 *
 * <p>The swarm analyses policies and produces hypothetical re-ratings. If the APR change
 * exceeds 0.5%, the orchestrator pauses for underwriter approval before completing.
 */
@Component(id = "policy-re-rating")
public class PolicyReRatingSwarm extends Swarm<String, RatedPolicyOutput> {

  @Override
  protected String instructions() {
    return """
        Perform on-demand analysis of the insurance policies identified in the user input
        and reply with hypothetical changes to those policies based on the additional fields
        in user input.

        Use the records agent to retrieve current policy data and rating history.

        If the value of a customer's Annual Percentage Rate in the hypothetical is more than
        0.5% different than the current rating, you will send a notification to the underwriting
        team with a diff comparison of the old and new policies using the notifyUnderwriter tool.
        You will also pause and await external interaction from authorised personnel.

        Once approval is received, finalize the re-rated policy output.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent("policy-records-agent")
            .withDescription("Retrieves current policy data and rating history"));
  }

  @Override
  protected Class<RatedPolicyOutput> resultType() {
    return RatedPolicyOutput.class;
  }

  @Override
  protected List<Object> tools() {
    return List.of(new UnderwriterNotifier());
  }

  @Override
  protected int maxTurns() {
    return 10;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
