package demo.swarm_class;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.Swarm;
import demo.swarm4.RatedPolicyOutput;
import demo.swarm4.UnderwriterNotifier;

import java.util.List;

/**
 * Insurance policy re-rating swarm — demonstrates dynamic tools based on input.
 *
 * <p>Uses {@link #getInput()} to decide whether to include the underwriter
 * notification tool. Only "commercial" or "high-value" policies require the
 * approval workflow.
 */
@Component(id = "policy-re-rating")
public class PolicyReRatingSwarm extends Swarm<String, RatedPolicyOutput> {

  @Override
  protected String instructions() {
    return """
        Perform on-demand analysis of the insurance policies identified in the user input
        and reply with hypothetical changes based on the additional fields in user input.

        Use the records agent to retrieve current policy data and rating history.

        If the APR change exceeds 0.5%, notify the underwriting team and pause
        for approval before finalizing.""";
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
    String input = getInput();
    boolean requiresApproval = input.contains("commercial")
        || input.contains("high-value");

    if (requiresApproval) {
      return List.of(new UnderwriterNotifier());
    }
    return List.of();
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
