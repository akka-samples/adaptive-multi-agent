package demo.swarm2;

import java.util.List;

/**
 * Structured response from the activity planner swarm.
 * The swarm terminates successfully when the LLM produces output conforming to this type.
 */
public record ActivityRecommendation(
    List<DayPlan> days,
    String summary) {

  public record DayPlan(
      String date,
      String weather,
      List<String> activities,
      String allergenLevel) {}
}
