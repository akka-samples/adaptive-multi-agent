package demo.swarm_class;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.Swarm;
import demo.swarm5.ActivityBookingResult;

import java.util.List;

/**
 * Swarm-of-swarms booking example — demonstrates dynamic handoffs based on input.
 *
 * <p>Uses {@link #getInput()} to decide whether to include booking handoffs.
 * If the user wants to book, the ticketing swarm and bookability agent are available.
 * Otherwise, only the activity planner is offered.
 */
@Component(id = "activity-booking")
public class ActivityBookingSwarm extends Swarm<String, ActivityBookingResult> {

  @Override
  protected String instructions() {
    return """
        You coordinate activity recommendations and optional booking.

        Step 1: Hand off to the activity planner to get recommendations based
        on the user's input, weather, availability, and allergen levels.

        Step 2: If the user wants to book, hand off to the bookability agent
        to check availability. If bookable, PAUSE for user confirmation.
        When resumed, hand off to the ticketing swarm to complete the booking.

        If the user only wants recommendations (no booking), compile the
        activity suggestions and complete.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    String input = getInput();
    boolean wantsBooking = input.toLowerCase().contains("book")
        || input.toLowerCase().contains("reserve")
        || input.toLowerCase().contains("ticket");

    if (wantsBooking) {
      return List.of(
          Handoff.toSwarm(ActivityPlannerSwarm.class)
              .withDescription("Plans outdoor activities based on weather, calendar, and allergens"),
          Handoff.toAgent("bookability-agent")
              .withDescription("Checks if an activity can be booked"),
          Handoff.toSwarm(TicketingSwarm.class)
              .withDescription("Handles ticket reservation, payment, and confirmation"));
    } else {
      return List.of(
          Handoff.toSwarm(ActivityPlannerSwarm.class)
              .withDescription("Plans outdoor activities based on weather, calendar, and allergens"));
    }
  }

  @Override
  protected Class<ActivityBookingResult> resultType() {
    return ActivityBookingResult.class;
  }

  @Override
  protected int maxTurns() {
    String input = getInput();
    boolean wantsBooking = input.toLowerCase().contains("book")
        || input.toLowerCase().contains("reserve")
        || input.toLowerCase().contains("ticket");
    return wantsBooking ? 15 : 10;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
