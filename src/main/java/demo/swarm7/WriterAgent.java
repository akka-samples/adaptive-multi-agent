package demo.swarm7;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "writer-agent")
public class WriterAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
      """
      You are a professional Content Creator. Your goal is to write a high-quality article or post based on the provided research.
      
      Adapt your tone and structure to the requested 'Writing Style' (e.g., Technical, Creative, Journalistic, etc.).
      Use the provided facts effectively to build a compelling narrative.
      """;

  public record WriterRequest(String topic, String facts, String writingStyle) {}

  public Effect<String> write(WriterRequest request) {
    String userMessage = String.format(
        "Topic: %s\n\nFacts:\n%s\n\nWriting Style: %s",
        request.topic(), request.facts(), request.writingStyle());

    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userMessage)
        .thenReply();
  }
}
