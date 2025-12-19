package demo.multiagent2.application;

import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.pattern.PlanStep;
import demo.multiagent2.domain.AgentSelection;

import java.util.List;

@Component(
  id = "planner-agent",
  name = "Planner",
  description = """
  An agent that analyzes the user request and available agents to plan the tasks
  to produce a suitable answer.
  """
)
public class PlannerAgent extends Agent {

  public record Request(String message, AgentSelection agentSelection) {}

  public record Plan(List<PlanStep> steps) {}

  private final AgentRegistry agentsRegistry;

  public PlannerAgent(AgentRegistry agentsRegistry) {
    this.agentsRegistry = agentsRegistry;
  }

  private String buildSystemMessage(AgentSelection agentSelection) {
    var agents = agentSelection.agents().stream().map(agentsRegistry::agentInfo).toList();
    return """
      Your job is to analyse the user request and the list of agents and devise the
      best order in which the agents should be called in order to produce a
      suitable answer to the user.

      You can find the list of exiting agents below (in JSON format):
      %s

      Note that each agent has a description of its capabilities.
      Given the user request, you must define the right ordering.

      Moreover, you must generate a concise request to be sent to each agent.
      This agent request is of course based on the user original request,
      but is tailored to the specific agent. Each individual agent should not
      receive requests or any text that is not related with its domain of expertise.

      PARALLEL EXECUTION:
      You can specify that multiple agents should run in parallel when their tasks
      are independent and don't depend on each other's results. Use the "parallel"
      structure to group agents that can run concurrently.

      Your response should follow a strict json schema as defined below.

      For sequential execution:
       {
         "steps": [
            { "agentId": "agent-1", "instruction": "tailored query for agent 1" },
            { "agentId": "agent-2", "instruction": "tailored query for agent 2" }
         ]
       }

      For parallel execution (agents in the "steps" array run concurrently):
       {
         "steps": [
            {
              "steps": [
                { "agentId": "agent-1", "instruction": "query for agent 1" },
                { "agentId": "agent-2", "instruction": "query for agent 2" }
              ]
            }
         ]
       }

      You can mix sequential and parallel:
       {
         "steps": [
            { "agentId": "info-gatherer", "instruction": "gather initial data" },
            {
              "steps": [
                { "agentId": "weather-agent", "instruction": "get weather" },
                { "agentId": "news-agent", "instruction": "get news" }
              ]
            },
            { "agentId": "summarizer", "instruction": "combine all information" }
         ]
       }

      The '<the id of the agent>' should be filled with the agent id.
      The '<tailored query>' should contain the agent tailored message.
      Use parallel execution when agents can run independently.

      Do not include any explanations or text outside of the JSON structure.

    """.stripIndent()
      .formatted(JsonSupport.encodeToString(agents));
  }

  public Effect<Plan> createPlan(Request request) {
    if (request.agentSelection.agents().size() == 1) {
      // no need to call an LLM to make a plan where selection has a single agent
      var step = PlanStep.of(request.agentSelection.agents().getFirst(), request.message());
      return effects().reply(new Plan(List.of(step)));
    } else {
      return effects()
        .systemMessage(buildSystemMessage(request.agentSelection))
        .userMessage(request.message())
        .responseConformsTo(Plan.class)
        .thenReply();
    }
  }
}
