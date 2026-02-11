package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class2.SwarmParams;
import demo.swarm5.ActivityBookingResult;

/**
 * Activity booking swarm — demonstrates dynamic configuration based on user input.
 *
 * <p>The {@code parameters()} method inspects the user message to decide which
 * handoffs to include. If the user wants to book, the ticketing swarm is available.
 * If they just want recommendations, only the planner is offered.
 *
 * <p>This is the key advantage of the single {@code parameters()} method over
 * separate abstract methods — the swarm shape can vary per invocation.
 */
@Component(id = "activity-booking-v2")
public class ActivityBookingSwarm extends Swarm<ActivityBookingResult> {

  @Override
  protected SwarmParams parameters(String userMessage) {
    boolean wantsBooking = userMessage.toLowerCase().contains("book")
        || userMessage.toLowerCase().contains("reserve")
        || userMessage.toLowerCase().contains("ticket");

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
      // Simpler swarm — no booking handoffs, fewer turns needed
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
  public Effect<Void> run(String userMessage) {
    return super.run(userMessage);
  }
}
