package demo.swarm5;

import java.util.Optional;

/**
 * Final result from the end-to-end activity booking swarm.
 */
public record ActivityBookingResult(
    String recommendedActivity,
    String date,
    String location,
    boolean booked,
    Optional<BookingConfirmation> booking) {

  public record BookingConfirmation(
      String confirmationId,
      String activityName,
      String venue,
      String date,
      int numberOfTickets,
      double totalPrice,
      String currency) {}
}
