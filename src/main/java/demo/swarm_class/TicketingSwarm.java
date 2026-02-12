package demo.swarm_class;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.Swarm;
import demo.swarm5.ActivityBookingResult;

import java.util.List;

/**
 * Ticketing swarm — handles ticket reservation, payment, and booking confirmation.
 * Extracted as a registered swarm class for composition in {@link ActivityBookingSwarm}.
 */
@Component(id = "ticketing")
public class TicketingSwarm extends Swarm<String, ActivityBookingResult> {

  @Override
  protected String instructions() {
    return """
        Book tickets for the activity that was recommended and approved by the user.

        Steps:
        1. Search for available tickets using the reservation agent
        2. Process payment using the payment agent
        3. Confirm the booking and provide confirmation details

        If tickets are unavailable, suggest the closest alternative date.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent("reservation-agent")
            .withDescription("Searches venues and reserves tickets for activities"),
        Handoff.toAgent("payment-agent")
            .withDescription("Processes payments for ticket purchases"),
        Handoff.toAgent("confirmation-agent")
            .withDescription("Sends booking confirmations via email"));
  }

  @Override
  protected Class<ActivityBookingResult> resultType() {
    return ActivityBookingResult.class;
  }

  @Override
  protected int maxTurns() {
    return 8;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
