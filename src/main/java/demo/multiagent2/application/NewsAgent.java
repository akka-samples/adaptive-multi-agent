package demo.multiagent2.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import demo.multiagent2.domain.AgentRequest;

@Component(id = "news-agent")
public class NewsAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are a news agent. Your job is to provide current news and information
    relevant to the user's query. Focus on recent events, trends, and updates.
    """.stripIndent();

  public Effect<String> process(AgentRequest request) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(request.message())
      .thenReply();
  }
}
