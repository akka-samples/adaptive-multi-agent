package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class.SwarmParams;
import demo.swarm5.ActivityBookingResult;

/**
 * Ticketing swarm — handles reservation, payment, and confirmation.
 */
@Component(id = "ticketing-v2")
public class TicketingSwarm extends Swarm<String, ActivityBookingResult> {

  @Override
  protected SwarmParams parameters() {
    return SwarmParams.builder()
        .instructions("""
            Book tickets for the activity that was recommended and approved by the user.

            Steps:
            1. Search for available tickets using the reservation agent
            2. Process payment using the payment agent
            3. Confirm the booking and provide confirmation details

            If tickets are unavailable, suggest the closest alternative date.""")
        .handoffs(
            Handoff.toAgent("reservation-agent")
                .withDescription("Searches venues and reserves tickets for activities"),
            Handoff.toAgent("payment-agent")
                .withDescription("Processes payments for ticket purchases"),
            Handoff.toAgent("confirmation-agent")
                .withDescription("Sends booking confirmations via email"))
        .maxTurns(8)
        .build();
  }

  @Override
  protected Class<ActivityBookingResult> resultType() {
    return ActivityBookingResult.class;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
