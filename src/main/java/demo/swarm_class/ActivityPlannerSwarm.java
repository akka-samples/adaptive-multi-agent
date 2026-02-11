package demo.swarm_class;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.Swarm;
import demo.swarm2.ActivityRecommendation;

import java.util.List;

/**
 * Activity planner swarm — gathers weather, calendar, and allergen data to recommend
 * outdoor activities. Equivalent to the swarm2 example using the class-based design.
 */
@Component(id = "activity-planner")
public class ActivityPlannerSwarm extends Swarm<ActivityRecommendation> {

  @Override
  protected String instructions() {
    return """
        Using the available handoffs, gather information about weather, calendar
        availability, and allergen levels. Recommend outdoor activities that are
        safe and enjoyable. Exclude days with high allergen levels or calendar conflicts.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent("weather-agent"),
        Handoff.toAgent("calendar-agent"),
        Handoff.toAgent("allergen-agent"));
  }

  @Override
  protected Class<ActivityRecommendation> resultType() {
    return ActivityRecommendation.class;
  }

  // Workaround: the Akka annotation processor. The real Swarm component type would not need this.
  public Effect<Void> dummy() {
    return null;
  }
}
