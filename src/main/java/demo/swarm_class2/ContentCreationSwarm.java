package demo.swarm_class2;

import akka.javasdk.annotations.Component;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class2.Swarm;
import akka.javasdk.swarm_class2.SwarmParams;
import demo.swarm7.ContentResult;

/**
 * Content creation pipeline swarm — multi-phase: research, write, edit, evaluate, HITL approval.
 */
@Component(id = "content-creation-v2")
public class ContentCreationSwarm extends Swarm<ContentResult> {

  @Override
  protected SwarmParams parameters(String userMessage) {
    return SwarmParams.builder()
        .instructions("""
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
              and loop back through research → write → edit → evaluate""")
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
        .build();
  }

  @Override
  protected Class<ContentResult> resultType() {
    return ContentResult.class;
  }

  // Workaround: Akka annotation processor requires a public method returning
  // Workflow.Effect on the concrete class. Not needed with a real Swarm component type.
  @Override
  public Effect<Void> run(String userMessage) {
    return super.run(userMessage);
  }
}
