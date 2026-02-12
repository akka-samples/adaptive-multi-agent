package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class2.SwarmParams;
import demo.swarm4.RatedPolicyOutput;
import demo.swarm4.UnderwriterNotifier;

/**
 * Policy re-rating swarm — demonstrates dynamic tools based on input.
 *
 * <p>The underwriter notification tool is only included for certain policy types
 * that require human approval workflows.
 */
@Component(id = "policy-re-rating-v2")
public class PolicyReRatingSwarm extends Swarm<String, RatedPolicyOutput> {

  @Override
  protected SwarmParams parameters() {
    String input = getInput();
    boolean requiresApproval = input.contains("commercial")
        || input.contains("high-value");

    var builder = SwarmParams.builder()
        .instructions("""
            Perform on-demand analysis of the insurance policies identified in the user input
            and reply with hypothetical changes based on the additional fields in user input.

            Use the records agent to retrieve current policy data and rating history.

            If the APR change exceeds 0.5%, notify the underwriting team and pause
            for approval before finalizing.""")
        .handoffs(
            Handoff.toAgent("policy-records-agent")
                .withDescription("Retrieves current policy data and rating history"))
        .maxTurns(10);

    if (requiresApproval) {
      builder.tools(new UnderwriterNotifier());
    }

    return builder.build();
  }

  @Override
  protected Class<RatedPolicyOutput> resultType() {
    return RatedPolicyOutput.class;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
