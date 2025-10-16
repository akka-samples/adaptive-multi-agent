package demo.multiagent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.Component;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The LedgerAgent manages the task ledger (facts and plan) in the MagenticOne pattern.
 * It gathers facts, creates plans, and updates them when the workflow needs to replan.
 */
@Component(id = "ledger-agent")
@AgentDescription(
  name = "Ledger Agent",
  description = """
    An agent that maintains the task ledger by gathering facts and creating
    execution plans for multi-agent workflows.
  """
)
public class LedgerAgent extends Agent {

  /**
   * Request types for the ledger agent operations.
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
    {
      @JsonSubTypes.Type(value = GatherFactsRequest.class, name = "gatherFacts"),
      @JsonSubTypes.Type(value = CreatePlanRequest.class, name = "createPlan"),
      @JsonSubTypes.Type(value = UpdateFactsRequest.class, name = "updateFacts"),
      @JsonSubTypes.Type(value = UpdatePlanRequest.class, name = "updatePlan"),
    }
  )
  public sealed interface Request
    permits GatherFactsRequest, CreatePlanRequest, UpdateFactsRequest, UpdatePlanRequest {}

  public record GatherFactsRequest(String task) implements Request {}

  public record CreatePlanRequest(String task, String teamDescription, String facts)
    implements Request {}

  public record UpdateFactsRequest(String task, String oldFacts) implements Request {}

  public record UpdatePlanRequest(String teamDescription) implements Request {}

  private static final String GATHER_FACTS_SYSTEM_PROMPT =
    """
    You are a fact-gathering analyst with Ken Jennings-level trivia knowledge and Mensa-level
    puzzle-solving abilities.

    When given a task, analyze it and provide a pre-survey covering:

    1. GIVEN OR VERIFIED FACTS - specific facts or figures provided in the request itself
    2. FACTS TO LOOK UP - facts that need to be researched and where they might be found
    3. FACTS TO DERIVE - facts that need logical deduction, simulation, or computation
    4. EDUCATED GUESSES - recalled knowledge, hunches, or well-reasoned guesses

    Keep in mind that "facts" are typically specific names, dates, statistics, etc.
    Use ONLY these four headings. DO NOT include other sections or list next steps.
    """.stripIndent();

  private static final String CREATE_PLAN_SYSTEM_PROMPT =
    """
    You are a strategic planner who creates execution plans for multi-agent systems.

    Given a task, available team members, and gathered facts, devise a short bullet-point plan
    for addressing the request. Consider each team member's expertise and capabilities.

    Only use the team members available - do not reference external people or services.

    Remember: not all team members need to be involved - only select those whose expertise
    is relevant to the specific task.
    """.stripIndent();

  private static final String UPDATE_FACTS_SYSTEM_PROMPT =
    """
    You are a knowledge synthesizer who updates fact sheets based on new learnings.

    Review the conversation history and the current fact sheet. Update it to include any new
    information discovered. Focus on:
    - Moving educated guesses to verified facts if they've been confirmed
    - Adding new guesses based on what we've learned
    - Refining facts based on actual results

    Always add or update at least one educated guess or hunch with your reasoning.
    This is your chance to incorporate lessons learned.
    """.stripIndent();

  private static final String UPDATE_PLAN_SYSTEM_PROMPT =
    """
    You are a strategic replanner who learns from failures and creates improved plans.

    Analyze what went wrong in the previous execution, identify the root cause, and create
    a new plan that:
    - Addresses the specific issues that caused the stall
    - Includes hints to overcome prior challenges
    - Avoids repeating the same mistakes
    - Remains concise and expressed in bullet-point form

    Only use the team members available - do not reference external people or services.
    """.stripIndent();

  /**
   * Single command handler that dispatches based on request type.
   */
  public Effect<String> process(Request request) {
    return switch (request) {
      case GatherFactsRequest req -> handleGatherFacts(req);
      case CreatePlanRequest req -> handleCreatePlan(req);
      case UpdateFactsRequest req -> handleUpdateFacts(req);
      case UpdatePlanRequest req -> handleUpdatePlan(req);
    };
  }

  private Effect<String> handleGatherFacts(GatherFactsRequest request) {
    String userMessage =
      """
      Here is the task to analyze:

      %s

      Please provide the fact-gathering pre-survey as instructed.
      """.stripIndent()
        .formatted(request.task());

    return effects()
      .systemMessage(GATHER_FACTS_SYSTEM_PROMPT)
      .userMessage(userMessage)
      .thenReply();
  }

  private Effect<String> handleCreatePlan(CreatePlanRequest request) {
    String userMessage =
      """
      Task: %s

      Available team members (id and description):
      %s

      Gathered facts:
      %s

      Please create an execution plan.
      """.stripIndent()
        .formatted(request.task(), request.teamDescription(), request.facts());

    return effects()
      .systemMessage(CREATE_PLAN_SYSTEM_PROMPT)
      .userMessage(userMessage)
      .thenReply();
  }

  private Effect<String> handleUpdateFacts(UpdateFactsRequest request) {
    String userMessage =
      """
      Task: %s

      Current fact sheet:
      %s

      Please update the fact sheet based on what we've learned from the conversation.
      """.stripIndent()
        .formatted(request.task(), request.oldFacts());

    return effects()
      .systemMessage(UPDATE_FACTS_SYSTEM_PROMPT)
      .userMessage(userMessage)
      .thenReply();
  }

  private Effect<String> handleUpdatePlan(UpdatePlanRequest request) {
    String userMessage =
      """
      Available team members (id and description):
      %s

      Based on the conversation history showing what went wrong, please create a new plan.
      """.stripIndent()
        .formatted(request.teamDescription());

    return effects()
      .systemMessage(UPDATE_PLAN_SYSTEM_PROMPT)
      .userMessage(userMessage)
      .thenReply();
  }
}
