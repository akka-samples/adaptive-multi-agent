package demo.swarm7;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "evaluator-agent")
public class EvaluatorAgent extends Agent {

  public record EvaluationResult(boolean isComplete, String feedback) {}

  private static final String SYSTEM_MESSAGE =
      """
You are a critical quality assurance expert. Your job is to evaluate if the drafted content sufficiently covers the requested topic.
      
      Compare the content against the original topic.
      - If it covers the topic well, set 'isComplete' to true.
      - If it misses key aspects or is too shallow, set 'isComplete' to false and provide specific 'feedback' on what is missing.
      
      Respond ONLY with the JSON structure.
      """;

  public record EvaluationRequest(String topic, String content) {}

  public Effect<EvaluationResult> evaluate(EvaluationRequest request) {
    String userMessage = String.format(
        "Topic: %s\n\nContent:\n%s",
        request.topic(), request.content());

    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userMessage)
        .responseConformsTo(EvaluationResult.class)
        .thenReply();
  }
}

