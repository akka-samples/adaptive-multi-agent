package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class.SwarmParams;
import demo.swarm2.ActivityRecommendation;
import demo.swarm2.AllergenAgent;
import demo.swarm2.CalendarAgent;
import demo.swarm2.WeatherAgent;

/**
 * Activity planner swarm — simple case where parameters are static.
 */
@Component(id = "activity-planner-v2")
public class ActivityPlannerSwarm extends Swarm<String, ActivityRecommendation> {

  @Override
  protected SwarmParams parameters() {
    return SwarmParams.builder()
        .instructions("""
            Using the available handoffs, gather information about weather, calendar
            availability, and allergen levels. Recommend outdoor activities that are
            safe and enjoyable. Exclude days with high allergen levels or calendar conflicts.""")
        .handoffs(
            Handoff.toAgent(WeatherAgent.class),
            Handoff.toAgent(CalendarAgent.class),
            Handoff.toAgent(AllergenAgent.class))
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
  public Effect<Void> run(String input) {
    return super.run(input);
  }
}
