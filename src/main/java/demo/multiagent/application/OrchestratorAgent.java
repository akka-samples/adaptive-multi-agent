package demo.multiagent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.Component;
import demo.multiagent.domain.ProgressEvaluation;
import java.util.List;

/**
 * The OrchestratorAgent evaluates workflow progress and determines next steps
 * in the MagenticOne pattern.
 */
@Component(id = "orchestrator-agent")
@AgentDescription(
  name = "Orchestrator",
  description = """
    An agent that evaluates the current progress of a multi-agent workflow
    and determines whether the task is complete, if progress is being made,
    and which agent should act next.
  """
)
public class OrchestratorAgent extends Agent {

  public record Request(
    String task,
    String teamDescription,
    List<String> participantNames,
    int roundCount
  ) {}

  private static final String SYSTEM_PROMPT =
    """
    You are a progress orchestrator for multi-agent workflows. Your role is to evaluate the
    current state of a task and make strategic decisions about next steps.

    For each evaluation, analyze the conversation history and answer these questions with reasoning:

    1. Is the request fully satisfied?
       - True if the original request has been SUCCESSFULLY and FULLY addressed
       - False if work remains to be done

    2. Are we in a loop?
       - Detect if agents are repeating the same requests or getting the same responses
       - Loops can span multiple turns and include repeated actions

    3. Are we making forward progress?
       - True if just starting, or recent messages are adding value
       - False if stuck in a loop or facing significant barriers (e.g., cannot read required files)

    4. Who should speak next?
       - Select from the available agent IDs based on what needs to be done next
       - Consider each agent's expertise and the current state of the task
       - Return the agent ID (not the name)

    5. What instruction should you give them?
       - Phrase as if speaking directly to the agent
       - Include specific information they need to complete their task

    Your response must be in pure JSON format. DO NOT include any text outside the JSON structure.
    """.stripIndent();

  public Effect<ProgressEvaluation> evaluateProgress(Request request) {
    String participantIds = String.join(", ", request.participantNames());
    String userMessage =
      """
      Current task:
      %s

      Available team members (format: "id: description"):
      %s

      Agent IDs to select from: %s

      Round: %d

      Based on the conversation history, evaluate the current progress and select the next agent by ID.
      """.stripIndent()
        .formatted(
          request.task(),
          request.teamDescription(),
          participantIds,
          request.roundCount()
        );

    return effects()
      .systemMessage(SYSTEM_PROMPT)
      .userMessage(userMessage)
      .responseConformsTo(ProgressEvaluation.class)
      .thenReply();
  }
}
