package demo.swarm5;

import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.Swarm;
import akka.javasdk.swarm.SwarmParams;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.client.ComponentClient;

/**
 * Swarm-of-swarms example — end-to-end activity recommendation and booking.
 *
 * The entire flow is expressed declaratively with nested SwarmParams and prompts.
 * No custom workflow or orchestration code is needed.
 *
 * Flow:
 * 1. Activity planner swarm gathers weather/calendar/allergen data and recommends activities
 * 2. Bookability agent checks if the recommended activity can be booked
 * 3. If bookable, the swarm pauses for user confirmation (HITL)
 * 4. On resume, the ticketing swarm handles reservation, purchase, and confirmation
 */
public class ActivityBookingExample {

  private final ComponentClient componentClient;

  public ActivityBookingExample(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public void startBookingFlow(String swarmId, String userInput) {

    // Inner swarm: activity planner (same as swarm2, reused via composition)
    var activityPlannerSwarm = SwarmParams.builder()
        .userMessage(userInput)
        .instructions("""
            Using the available handoffs, gather information about weather, calendar
            availability, and allergen levels. Recommend outdoor activities that are
            safe and enjoyable. Exclude days with high allergen levels or calendar conflicts.""")
        .handoffs(
            Handoff.toAgent("weather-agent"),
            Handoff.toAgent("calendar-agent"),
            Handoff.toAgent("allergen-agent"))
        .maxTurns(10)
        .build();

    // Inner swarm: ticketing (reserve, purchase, confirm)
    var ticketingSwarm = SwarmParams.builder()
        .userMessage(userInput)
        .instructions("""
            Book tickets for the activity that was recommended and approved by the user.
            Use the session context to find the recommended activity details.

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

    // Outer swarm: orchestrates the full flow with HITL pause
    componentClient
        .forSwarm(swarmId)
        .method(Swarm::run)
        .invoke(SwarmParams.builder()
            .userMessage(userInput)
            .instructions("""
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

                If the activity is not bookable, suggest alternatives and do not pause.""")
            .responseAs(ActivityBookingResult.class)
            .handoffs(
                Handoff.toSwarm("activity-planner", activityPlannerSwarm)
                    .withDescription("Plans outdoor activities based on weather, calendar, and allergens"),
                Handoff.toAgent("bookability-agent")
                    .withDescription("Checks if an activity can be booked and returns availability and pricing"),
                Handoff.toSwarm("ticketing", ticketingSwarm)
                    .withDescription("Handles ticket reservation, payment, and booking confirmation"))
            .maxTurns(15)
            .build());
  }

  /**
   * Check the current state — may be paused awaiting user booking confirmation.
   */
  public SwarmResult checkStatus(String swarmId) {
    return componentClient
        .forSwarm(swarmId)
        .method(Swarm::getResult)
        .invoke();
  }

  /**
   * User confirms they want to book — resume the swarm to proceed to ticketing.
   */
  public void confirmBooking(String swarmId) {
    componentClient
        .forSwarm(swarmId)
        .method(Swarm::resume)
        .invoke("User confirmed. Proceed with booking.");
  }

  /**
   * User declines — stop the swarm.
   */
  public void declineBooking(String swarmId) {
    componentClient
        .forSwarm(swarmId)
        .method(Swarm::stop)
        .invoke("User declined booking.");
  }
}
