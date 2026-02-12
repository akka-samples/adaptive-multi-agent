package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class.SwarmParams;
import demo.swarm5.ActivityBookingResult;

/**
 * Activity booking swarm — demonstrates dynamic configuration based on input.
 *
 * <p>The {@code parameters()} method uses {@code getInput()} to decide which
 * handoffs to include. If the user wants to book, the ticketing swarm is available.
 */
@Component(id = "activity-booking-v2")
public class ActivityBookingSwarm extends Swarm<String, ActivityBookingResult> {

  @Override
  protected SwarmParams parameters() {
    String input = getInput();
    boolean wantsBooking = input.toLowerCase().contains("book")
        || input.toLowerCase().contains("reserve")
        || input.toLowerCase().contains("ticket");

    var builder = SwarmParams.builder()
        .instructions("""
            You coordinate activity recommendations and optional booking.

            Step 1: Hand off to the activity planner to get recommendations based
            on the user's input, weather, availability, and allergen levels.

            Step 2: If the user wants to book, hand off to the bookability agent
            to check availability. If bookable, PAUSE for user confirmation.
            When resumed, hand off to the ticketing swarm to complete the booking.

            If the user only wants recommendations (no booking), compile the
            activity suggestions and complete.""")
        .maxTurns(15);

    if (wantsBooking) {
      builder.handoffs(
          Handoff.toSwarm(ActivityPlannerSwarm.class)
              .withDescription("Plans outdoor activities based on weather, calendar, and allergens"),
          Handoff.toAgent("bookability-agent")
              .withDescription("Checks if an activity can be booked"),
          Handoff.toSwarm(TicketingSwarm.class)
              .withDescription("Handles ticket reservation, payment, and confirmation"));
    } else {
      builder
          .handoffs(
              Handoff.toSwarm(ActivityPlannerSwarm.class)
                  .withDescription("Plans outdoor activities based on weather, calendar, and allergens"))
          .maxTurns(10);
    }

    return builder.build();
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
