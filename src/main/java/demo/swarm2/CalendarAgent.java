package demo.swarm2;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
    id = "calendar-agent",
    name = "Calendar Agent",
    description = "Checks the user's calendar availability for specific dates.")
public class CalendarAgent extends Agent {

  public Effect<String> query(String message) {
    return effects()
        .systemMessage("You are a calendar agent. Check availability for the requested dates.")
        .userMessage(message)
        .thenReply();
  }
}
