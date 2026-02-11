package demo.swarm_class;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.Swarm;
import demo.swarm5.ActivityBookingResult;

import java.util.List;

/**
 * Swarm-of-swarms booking example — equivalent to swarm5 using the class-based design.
 *
 * <p>Demonstrates composition: the outer swarm hands off to inner swarms (activity planner,
 * ticketing) and individual agents (bookability). The parent pauses while a child swarm runs.
 *
 * <p>Both agent and swarm handoffs use the same symmetric pattern — by class or by ID.
 */
@Component(id = "activity-booking")
public class ActivityBookingSwarm extends Swarm<ActivityBookingResult> {

  @Override
  protected String instructions() {
    return """
        You coordinate an end-to-end activity recommendation and booking flow.

        Step 1: Hand off to the activity planner to get recommendations based
        on the user's input, weather, availability, and allergen levels.

        Step 2: Once you have a recommendation, hand off to the bookability
        agent to check if tickets or reservations are available for that activity.

        Step 3: If the activity is bookable, PAUSE and wait for user confirmation.
        Include the activity details, date, location, and estimated price in
        the pause message so the user can make an informed decision.

        Step 4: When resumed (user confirmed), hand off to the ticketing swarm
        to complete the reservation, payment, and confirmation.

        If the activity is not bookable, suggest alternatives and do not pause.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toSwarm(ActivityPlannerSwarm.class)
            .withDescription("Plans outdoor activities based on weather, calendar, and allergens"),
        Handoff.toAgent("bookability-agent")
            .withDescription("Checks if an activity can be booked and returns availability and pricing"),
        Handoff.toSwarm(TicketingSwarm.class)
            .withDescription("Handles ticket reservation, payment, and booking confirmation"));
  }

  @Override
  protected Class<ActivityBookingResult> resultType() {
    return ActivityBookingResult.class;
  }

  @Override
  protected int maxTurns() {
    return 15;
  }
  // Workaround: the Akka annotation processor. The real Swarm component type would not need this.
  public Effect<Void> dummy() {
    return null;
  }
}
