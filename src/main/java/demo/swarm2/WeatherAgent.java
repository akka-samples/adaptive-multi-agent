package demo.swarm2;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(
    id = "weather-agent",
    name = "Weather Agent",
    description = "Provides weather forecasts and conditions for specific locations and dates.")
public class WeatherAgent extends Agent {

  public Effect<String> query(String message) {
    return effects()
        .systemMessage("""
            You are a weather agent. Provide weather forecasts including temperature,
            conditions, humidity, and wind speed for the requested location and dates.""")
        .userMessage(message)
        .thenReply();
  }
}
