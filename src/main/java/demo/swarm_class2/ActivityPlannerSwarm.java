package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class2.SwarmParams;
import demo.swarm2.ActivityRecommendation;

/**
 * Activity planner swarm — simple case where parameters are static.
 * Equivalent to the swarm_class version, showing the builder pattern.
 */
@Component(id = "activity-planner-v2")
public class ActivityPlannerSwarm extends Swarm<ActivityRecommendation> {

  @Override
  protected SwarmParams parameters(String userMessage) {
    return SwarmParams.builder()
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
  }

  @Override
  protected Class<ActivityRecommendation> resultType() {
    return ActivityRecommendation.class;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String userMessage) {
    return super.run(userMessage);
  }
}
