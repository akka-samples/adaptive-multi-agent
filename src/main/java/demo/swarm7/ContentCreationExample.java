package demo.swarm7;

import akka.NotUsed;
import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.Swarm;
import akka.javasdk.swarm.SwarmEvent;
import akka.javasdk.swarm.SwarmParams;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.client.ComponentClient;
import akka.stream.javadsl.Source;

/**
 * Content creation pipeline using a swarm — refactored from ContentWorkflow.
 *
 * The original implementation used a 200+ line Workflow with 7 steps, explicit state
 * management, parallel research batching, and manual loop control. The swarm version
 * replaces all of that with instructions and handoffs.
 *
 * Flow:
 *   1. Orchestrator breaks the topic into sub-topics (replaces PlannerAgent)
 *   2. Hands off to researcher for each sub-topic (with web search + fetch tools)
 *   3. Hands off to writer with accumulated research
 *   4. Hands off to editor to polish
 *   5. Hands off to evaluator to check quality
 *   6. If evaluator finds gaps, loops back to researcher/writer/editor
 *   7. When quality is sufficient, pauses for human approval (HITL)
 *   8. On resume with approval, completes. On resume with feedback, loops back.
 */
public class ContentCreationExample {

  private static final String INSTRUCTIONS = """
      You are a content production orchestrator. Your goal is to produce a high-quality
      article on the user's topic in their requested writing style.

      Follow this process:

      PHASE 1 — Research
      Break the topic into 3-5 specific sub-topics that need research.
      For each sub-topic, hand off to the researcher agent. The researcher has web search
      and web fetch tools — it will gather deep, factual information from real sources.

      PHASE 2 — Writing
      Once you have sufficient research, hand off to the writer agent with:
      - The original topic
      - All accumulated research findings
      - The requested writing style

      PHASE 3 — Editing
      Hand off the draft to the editor agent for polishing (grammar, flow, readability).

      PHASE 4 — Evaluation
      Hand off the edited content to the evaluator agent, which will assess whether the
      content sufficiently covers the topic. The evaluator returns a structured response
      with 'isComplete' (boolean) and 'feedback' (string).

      If the evaluator says isComplete=false and you haven't exceeded 3 revision cycles:
      - Analyze the feedback to identify what's missing
      - Hand off to the researcher for the specific gaps
      - Then back to writer → editor → evaluator
      - Repeat until approved or 3 cycles reached

      PHASE 5 — Human review
      When the evaluator approves (or max cycles reached), PAUSE the swarm.
      Include the final content in the pause message so the human can review it.

      When resumed:
      - If the message indicates approval, compile the final result
      - If the message contains change requests, treat it as evaluator feedback
        and loop back through research → write → edit → evaluate
      """;

  private final ComponentClient componentClient;

  public ContentCreationExample(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public void start(String swarmId, String topic, String writingStyle) {

    String userMessage = "Topic: " + topic + "\nWriting Style: " + writingStyle;

    componentClient
        .forSwarm(swarmId)
        .method(Swarm::run)
        .invoke(SwarmParams.builder()
            .userMessage(userMessage)
            .instructions(INSTRUCTIONS)
            .resultAs(ContentResult.class)
            .handoffs(
                Handoff.toAgent("researcher-agent")
                    .withDescription("Researches a specific sub-topic using web search and "
                        + "web page fetching. Returns synthesized findings."),
                Handoff.toAgent("writer-agent")
                    .withDescription("Writes content based on a topic, research facts, "
                        + "and a writing style."),
                Handoff.toAgent("editor-agent")
                    .withDescription("Polishes a draft for grammar, flow, readability, "
                        + "and professional tone."),
                Handoff.toAgent("evaluator-agent")
                    .withDescription("Evaluates content quality against the topic. Returns "
                        + "isComplete (boolean) and feedback (string)."))
            .maxTurns(25)
            .build());
  }

  public SwarmResult checkStatus(String swarmId) {
    return componentClient
        .forSwarm(swarmId)
        .method(Swarm::getResult)
        .invoke();
  }

  /**
   * Stream real-time progress events — agent handoffs, tool calls, pauses, completion.
   * Can be exposed as SSE from an endpoint to drive a live UI.
   */
  public Source<SwarmEvent, NotUsed> trackProgress(String swarmId) {
    return componentClient
        .forSwarm(swarmId)
        .streamMethod(Swarm::events)
        .source();
  }

  /** Human approves the content. */
  public void approve(String swarmId) {
    componentClient
        .forSwarm(swarmId)
        .method(Swarm::resume)
        .invoke("Approved. Finalize the content.");
  }

  /** Human requests changes — the swarm loops back through the pipeline. */
  public void requestChanges(String swarmId, String feedback) {
    componentClient
        .forSwarm(swarmId)
        .method(Swarm::resume)
        .invoke("Changes requested: " + feedback);
  }

  /** Human cancels the content creation. */
  public void abort(String swarmId) {
    componentClient
        .forSwarm(swarmId)
        .method(Swarm::stop)
        .invoke("User cancelled content creation.");
  }
}
