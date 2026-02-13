# Akka SDK Swarm Component - Design Document

## Executive Summary

This document proposes a **Swarm** component for the Akka SDK that provides on-demand, multi-agent orchestration with automatic looping, handoffs, and termination. A Swarm blends concepts from existing **Workflow** (orchestration, state management, pause/resume) and **Agent** (LLM interaction, tools, session memory) components to create a higher-level abstraction for agentic collaboration.

**Key Design Principle**: Swarms are **on-demand orchestrators** that coordinate multiple agents to achieve a specific goal through iterative loops and handoffs, then complete.

---

## Component Model

### Swarm is a pre-built Workflow

A Swarm is a **framework-provided Workflow** with a fixed step graph (the agent loop). The behavior of a swarm is a **user-defined class** that extends `Swarm<A, B>`, where `A` is the input type and `B` is the result type. The developer declares the swarm's behavior by overriding abstract methods — instructions, handoffs, result type, and optionally tools and max turns. The agent loop and built-in operations (pause, resume, stop) are provided by the framework.

Internally, the runtime implements the agent loop using the Workflow machinery (a fixed step graph), but the developer does not interact with Workflow APIs. The swarm class is annotated with `@Component(id = "...")` like other Akka components.

```java
@Component(id = "activity-planner")
public class ActivityPlannerSwarm extends Swarm<String, ActivityRecommendation> {

  @Override
  protected String instructions() { ... }

  @Override
  protected List<Handoff> handoffs() { ... }

  @Override
  protected Class<ActivityRecommendation> resultType() { ... }
}
```

**Why a user class?**
- **Type safety**: The two type parameters (`A` for input, `B` for result) flow through the entire API — the `SwarmClient`, `SwarmResult<B>`, and `run(A)` are all fully typed.
- **Dynamic configuration**: Override methods like `handoffs()`, `tools()`, and `instructions()` can use `getInput()` to inspect the runtime input and make dynamic decisions (e.g., include booking agents only when the user mentions "book").
- **Reusable composition**: Swarm classes are registered components that can be referenced by class in handoffs (`Handoff.toSwarm(TicketingSwarm.class)`), enabling type-safe swarm-of-swarms composition.
- **Consistent with Akka conventions**: Follows the same `@Component` + extend-a-base-class pattern as Entity, Workflow, and Agent.
- **Call-site overrides**: The `SwarmClient.withParameters(SwarmParams)` mechanism allows overriding a swarm's configuration at the call site without creating a new subclass, for ad-hoc customization.

**Key conceptual shift**: Today, the developer writes orchestration logic (Workflow steps decide which agent to call). With Swarm, the **LLM IS the orchestrator** — it decides which handoffs to invoke based on instructions, and the runtime manages the durable execution loop.

---

## Core Concepts

### What is a Swarm?

> "A decentralized, multi-agent system where multiple autonomous AI agents collaborate, communicate, and specialize to achieve shared, complex goals."

**Characteristics:**
- **On-demand execution**: Wake up → work → wrap up (not long-lived)
- **Specialization**: Each agent has a single responsibility
- **Handoffs**: Agents delegate to other agents via tool-like calls
- **Loops**: Iterate until goal achieved or constraints violated
- **Composition**: Swarms can hand off to other swarms (nested execution)
- **Resilience**: Failure handling, pause/resume, human-in-the-loop

### Handoffs

A **handoff** is when one agent delegates work to another. In implementation terms:
- Handoffs are **tool calls** with predictable patterns
- Can reference existing `@Component` agents by class or ID
- Can reference other `@Component` swarms by class or ID (composition)
- Enable dynamic agent selection at runtime via `getInput()`
- Managed by the swarm to provide durable execution

### Loop Termination Conditions

A swarm loop terminates when:

**Success Cases:**
1. LLM returns response with **no tool calls or handoffs**
2. LLM output **conforms to expected type** (strongly typed success via `resultType()`)
3. **Explicit termination** via special tool (e.g., `complete()`)

**Failure Cases:**
1. **Max iterations exceeded** (runaway prevention via `maxTurns()`)
2. **Fatal error** during LLM or tool execution
3. **Guardrail violation** (policy enforcement)
4. **Explicit stop** via `stop()` command

**Pause Case:**
1. **Human-in-the-loop** approval needed (business logic pause via built-in `pause` tool)

### Composition (Swarm of Swarms)

Swarms support **recursive composition** via class references:
- A swarm's `handoffs()` can include `Handoff.toSwarm(ChildSwarm.class)`
- Inner swarms have their own loops, instructions, and termination conditions
- Parent swarm waits for child swarms to complete
- Enables reuse of specialized swarms (e.g., `TicketingSwarm` called by `ActivityBookingSwarm`)

---

### API Design

#### Defining a Swarm

A swarm is defined by extending `Swarm<A, B>` and overriding abstract methods:

```java
@Component(id = "activity-planner")
public class ActivityPlannerSwarm extends Swarm<String, ActivityRecommendation> {

  @Override
  protected String instructions() {
    return """
        Using the available handoffs, gather information about weather, calendar
        availability, and allergen levels. Recommend outdoor activities that are
        safe and enjoyable. Exclude days with high allergen levels or calendar conflicts.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent(WeatherAgent.class),
        Handoff.toAgent(CalendarAgent.class),
        Handoff.toAgent(AllergenAgent.class));
  }

  @Override
  protected Class<ActivityRecommendation> resultType() {
    return ActivityRecommendation.class;
  }
}
```

**Swarm base class API:**

| Method | Required | Description |
|--------|----------|-------------|
| `instructions()` | Yes | System instructions for the orchestrator LLM |
| `handoffs()` | Yes | Handoff targets available to the orchestrator |
| `resultType()` | Yes | Expected result type; conforming LLM output terminates the swarm |
| `maxTurns()` | No | Max LLM round-trips (default: 10) |
| `tools()` | No | Tools available to the orchestrator LLM (default: none) |
| `getInput()` | N/A | Accessor for the runtime input, available in all override methods |

#### Starting a Swarm

```java
var swarmId = UUID.randomUUID().toString();
componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId)
    .run("Suggest outdoor activities for this weekend");
```

**Key Features:**
- `componentClient.forSwarm(SwarmClass.class, swarmId)` returns a typed `SwarmClient<A, B>`
- The swarm class determines both the input type `A` and result type `B`
- `swarmId` is both the unique instance identifier and the session ID for conversational memory shared across agents within the swarm
- `run(input)` starts the swarm asynchronously (fire-and-retrieve pattern)

#### Retrieving the Result

Since `run()` is returning void, the result is retrieved separately. `getResult()` returns a fully-typed `SwarmResult<B>` — a sealed interface ADT consumed via pattern matching.

Alternatively, `Swarm::run` could return the final result, but we would still need a separate `getResult`, because a swarm might be rather long lived, and outlives the client, and then it should still be possible to ask it for result (or notification stream).

```java
SwarmResult<ActivityRecommendation> result =
    componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).getResult();

switch (result) {
    case SwarmResult.Completed<ActivityRecommendation> c -> {
        ActivityRecommendation recommendation = c.result(); // fully typed, no cast
    }
    case SwarmResult.Paused<ActivityRecommendation> p -> {
        // awaiting human input
        // p.reason().message(), p.currentTurn()
    }
    case SwarmResult.Running<ActivityRecommendation> r -> {
        // still in progress
        // r.currentAgent(), r.currentTurn(), r.maxTurns()
    }
    case SwarmResult.Failed<ActivityRecommendation> f -> {
        // handle failure: f.reason()
    }
    case SwarmResult.Stopped<ActivityRecommendation> s -> {
        // externally stopped: s.reason()
    }
}
```

**SwarmResult model (sealed ADT):**
```java
sealed interface SwarmResult<R> {
    record Running<R>(int currentTurn, int maxTurns,
                      Optional<String> currentAgent,
                      Optional<String> currentChildSwarm) implements SwarmResult<R> {}
    record Paused<R>(PauseReason reason, int currentTurn, int maxTurns) implements SwarmResult<R> {}
    record Completed<R>(R result) implements SwarmResult<R> {}
    record Failed<R>(String reason) implements SwarmResult<R> {}
    record Stopped<R>(String reason) implements SwarmResult<R> {}
}
```

#### Notification Stream

Subscribe to a real-time stream of swarm events to track progress, agent handoffs, and completion.

```java
Source<SwarmEvent, NotUsed> events =
    componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).events();

// SwarmEvent is a sealed interface
sealed interface SwarmEvent {
    record Started(String swarmId, Instant timestamp) implements SwarmEvent {}
    record AgentHandoff(String fromAgent, String toAgent, Instant timestamp) implements SwarmEvent {}
    record ToolCall(String agent, String toolName, Instant timestamp) implements SwarmEvent {}
    record TurnCompleted(int turn, int maxTurns, String activeAgent) implements SwarmEvent {}
    record Paused(PauseReason reason, Instant timestamp) implements SwarmEvent {}
    record Resumed(Instant timestamp) implements SwarmEvent {}
    record Completed(String resultJson, Instant timestamp) implements SwarmEvent {}
    record Failed(String reason, Instant timestamp) implements SwarmEvent {}
}
```

#### Handoff Definition Patterns

```java
// Pattern 1: Reference agent by class (type-safe, resolves component ID from @Component)
Handoff.toAgent(WeatherAgent.class)

// Pattern 2: Reference agent by component ID (for agents not available as classes)
Handoff.toAgent("weather-agent")

// Pattern 3: Reference another swarm by class (composition)
Handoff.toSwarm(TicketingSwarm.class)

// Pattern 4: Reference another swarm by component ID
Handoff.toSwarm("ticketing")

// All patterns support an optional description for the LLM tool definition:
Handoff.toAgent(WeatherAgent.class)
    .withDescription("Provides weather information, forecasts, and conditions")

Handoff.toSwarm(TicketingSwarm.class)
    .withDescription("Handles ticket reservation, payment, and confirmation")
```

#### What the LLM sees

Handoffs are **metadata** that get compiled into the system prompt as available function tools. Agent metadata from `@Component(name, description)` annotations and `withDescription()` overrides are used to generate tool descriptions.

```
Available handoffs:
- handoff_to_weather_agent(request: string): "Provides weather
  information, forecasts, and conditions"
- handoff_to_activity_agent(request: string): "Suggests real-world
  activities like sports, games, attractions"
- handoff_to_ticketing(request: string): "Handles ticket reservation,
  payment, and confirmation"
```

#### How agent handoffs execute

When the orchestrator LLM calls a handoff to an `@Component` agent:
1. The swarm runtime detects the handoff tool call in the orchestrator's response
2. Calls the target agent using the swarm's session ID (agents within a swarm share the session)
3. The agent processes the request with its own system prompt and tools
4. The agent's response is fed back to the orchestrator LLM as the handoff tool result
5. Swarm state is updated durably (handoff record)
6. The orchestrator reasons about the result and decides what to do next

**This is the key difference between handoffs and regular tools:**
- **Regular tools**: Executed inline during the LLM interaction (within a single workflow step)
- **Handoff tools**: Intercepted by the swarm workflow, trigger durable workflow steps with state tracking

#### Dynamic Configuration Based on Input

Override methods can use `getInput()` to inspect the runtime input and make dynamic decisions.

**Dynamic handoffs:**
```java
@Component(id = "activity-booking")
public class ActivityBookingSwarm extends Swarm<String, ActivityBookingResult> {

  @Override
  protected List<Handoff> handoffs() {
    String input = getInput();
    boolean wantsBooking = input.toLowerCase().contains("book")
        || input.toLowerCase().contains("reserve");

    if (wantsBooking) {
      return List.of(
          Handoff.toSwarm(ActivityPlannerSwarm.class)
              .withDescription("Plans outdoor activities"),
          Handoff.toAgent("bookability-agent")
              .withDescription("Checks if an activity can be booked"),
          Handoff.toSwarm(TicketingSwarm.class)
              .withDescription("Handles ticket reservation and payment"));
    } else {
      return List.of(
          Handoff.toSwarm(ActivityPlannerSwarm.class)
              .withDescription("Plans outdoor activities"));
    }
  }

  @Override
  protected int maxTurns() {
    // More turns needed when booking is involved
    return getInput().toLowerCase().contains("book") ? 15 : 10;
  }

  // ...
}
```

**Dynamic tools:**
```java
@Component(id = "policy-re-rating")
public class PolicyReRatingSwarm extends Swarm<String, RatedPolicyOutput> {

  @Override
  protected List<Object> tools() {
    String input = getInput();
    boolean requiresApproval = input.contains("commercial")
        || input.contains("high-value");

    if (requiresApproval) {
      return List.of(new UnderwriterNotifier());
    }
    return List.of();
  }

  // ...
}
```

**Dynamic instructions:**
```java
@Component(id = "records-search")
public class RecordsSearchSwarm extends Swarm<String, UCCSearchResult> {

  @Override
  protected String instructions() {
    return """
        Search across all available record databases for UCC filings matching the
        property address. Return the search result with the highest accuracy score.

        User request: Find all UCC filings for the property at address """
        + getInput() + " where the property has been declared as collateral on a loan";
  }

  // ...
}
```

#### Tools in Swarms

Tools are declared by overriding the `tools()` method, just like handoffs:

```java
@Component(id = "policy-re-rating")
public class PolicyReRatingSwarm extends Swarm<String, RatedPolicyOutput> {

  @Override
  protected List<Object> tools() {
    return List.of(new UnderwriterNotifier());
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent("policy-records-agent")
            .withDescription("Retrieves current policy data and rating history"));
  }

  // ...
}
```

**Tool vs Handoff Distinction:**
- **Tools**: Functions that return data to the LLM (synchronous, deterministic)
- **Handoffs**: Delegation to another agent/swarm (may involve loops, LLM calls)

Internally, handoffs are **special tools** with naming conventions like `handoff_to_{agentId}`.

#### Call-Site Parameter Overrides

The `withParameters(SwarmParams)` mechanism allows overriding a swarm's configuration at the call site without creating a new subclass. The swarm class still determines the `resultType()`.

```java
// Standard content creation
componentClient.forSwarm(ContentCreationSwarm.class, swarmId)
    .run("Write an article about AI safety");

// Same swarm class, but with an extra fact-checker agent added at the call site
componentClient.forSwarm(ContentCreationSwarm.class, swarmId)
    .withParameters(SwarmParams.builder()
        .instructions("""
            You are a content production orchestrator with fact-checking.
            1. Research the topic using the researcher agent
            2. Write the content using the writer agent
            3. Edit using the editor agent
            4. Fact-check all claims using the fact-checker agent
            5. If inaccuracies found, send back to the writer with corrections
            6. Once approved, evaluate with the evaluator agent
            7. When approved, PAUSE for human review""")
        .handoffs(
            Handoff.toAgent(ResearcherAgent.class)
                .withDescription("Researches a specific sub-topic using web search"),
            Handoff.toAgent(WriterAgent.class)
                .withDescription("Writes content based on research and style"),
            Handoff.toAgent(EditorAgent.class)
                .withDescription("Polishes a draft for grammar and readability"),
            Handoff.toAgent("fact-checker-agent")
                .withDescription("Verifies factual claims and flags inaccuracies"),
            Handoff.toAgent(EvaluatorAgent.class)
                .withDescription("Evaluates content quality and completeness"))
        .maxTurns(30)
        .build())
    .run("Write an article about AI safety");
```

#### Pause, Resume, and Stop

```java
// Resume a paused swarm (e.g., after human approval)
componentClient.forSwarm(PolicyReRatingSwarm.class, swarmId)
    .resume("Underwriter approved change. Continue.");

// Stop a swarm permanently (terminal state)
componentClient.forSwarm(ActivityBookingSwarm.class, swarmId)
    .stop("User cancelled operation");

// Check swarm status
SwarmResult<RatedPolicyOutput> status =
    componentClient.forSwarm(PolicyReRatingSwarm.class, swarmId).getResult();
```

**PauseReason model:**
```java
record PauseReason(
    Type type,           // HITL, EMERGENCY, APPROVAL_NEEDED
    String message,
    Optional<String> pendingApprovalId
) {
    enum Type { HITL, EMERGENCY, APPROVAL_NEEDED }
}
```

#### Human-in-the-Loop Pattern

HITL is expressed in the swarm's instructions. The orchestrator LLM uses the built-in `pause` tool when human input is needed.

```java
@Component(id = "policy-re-rating")
public class PolicyReRatingSwarm extends Swarm<String, RatedPolicyOutput> {

  @Override
  protected String instructions() {
    return """
        Perform on-demand analysis of insurance policies.

        Use the records agent to retrieve current policy data and rating history.

        If the APR change exceeds 0.5%, notify the underwriting team and pause
        for approval before finalizing.""";
  }

  // Built-in tools available to all swarms:
  // - pause(reason, context): Pause swarm for HITL
  // - complete(result): Explicitly complete with result
  // - fail(reason): Explicitly fail with error

  // ...
}
```

```java
// Later, after human approval:
componentClient.forSwarm(PolicyReRatingSwarm.class, swarmId)
    .resume("Underwriter approved change. Continue.");
```

#### HTTP Endpoint Pattern

Swarms are consumed from HTTP endpoints following standard Akka patterns:

```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/swarm-class")
public class SwarmEndpoint {

  public record StartRequest(String message) {}
  public record ResumeRequest(String message) {}
  public record StartedResponse(String swarmId) {}

  private final ComponentClient componentClient;

  public SwarmEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/activity-planner")
  public HttpResponse startActivityPlanner(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  @Get("/activity-planner/{swarmId}")
  public HttpResponse getActivityPlannerResult(String swarmId) {
    return toResponse(
        componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).getResult());
  }

  @Post("/policy-re-rating/{swarmId}/resume")
  public HttpResponse resumePolicyReRating(String swarmId, ResumeRequest request) {
    componentClient.forSwarm(PolicyReRatingSwarm.class, swarmId).resume(request.message());
    return HttpResponses.ok();
  }

  private HttpResponse toResponse(SwarmResult<?> result) {
    return switch (result) {
      case SwarmResult.Completed<?> c -> HttpResponses.ok(c.result());
      case SwarmResult.Running<?> r -> HttpResponses.accepted("Swarm is still running");
      case SwarmResult.Paused<?> p -> HttpResponses.ok(p);
      case SwarmResult.Failed<?> f -> HttpResponses.badRequest(f.reason());
      case SwarmResult.Stopped<?> s -> HttpResponses.ok(s);
    };
  }
}
```

---

## The Agent Loop (Execution Model)

### One continuous conversation

A swarm is fundamentally **one ongoing conversation** with an orchestrator LLM. The swarm runtime starts the conversation (with instructions and a user message), and the orchestrator LLM drives everything through the standard **tool-calling loop**:

```
[start conversation with orchestrator LLM]
  → orchestrator responds (may include tool calls)
  → runtime executes requested tools / handoffs
  → results fed back to orchestrator
  → orchestrator responds again
  → ... (repeat until done)
```

The orchestrator LLM sees handoffs as ordinary tool calls. It calls `handoff_to_weather_agent("What's the forecast?")` and gets back the weather agent's response as if it were any other tool result. The orchestrator never "loses control" - it always gets results back and decides the next step.

### What happens at each round trip

Each time the orchestrator LLM responds, the runtime inspects the response and acts accordingly:

| Orchestrator response contains... | Runtime action |
|---------------------|----------------|
| **Regular tool calls** | Execute tools, feed results back to orchestrator, continue conversation |
| **Agent handoff call** | Call the target agent (as a durable workflow step), feed the agent's response back to the orchestrator as the tool result, continue conversation |
| **Swarm handoff call** | Spawn child swarm, pause parent. When child completes, feed its result back to the orchestrator, continue conversation |
| **`complete(result)` built-in tool** | Terminate successfully with the given result |
| **`pause(reason)`** | Pause workflow for HITL. On resume, continue conversation |
| **`fail(reason)`** | Terminate with failure |
| **Response matching `resultType()`** | Terminate successfully (type-match termination) |
| **Clean text response** (no tool calls) | Terminate (orchestrator is done) |

After each round trip the runtime also checks guards: max rounds exceeded? guardrail violated?

**What counts as a "round"?** One LLM response + execution of its tool calls. `maxTurns()` limits the number of these round trips to prevent runaway conversations.

### Durable execution boundaries

The tool-calling loop is the same pattern any LLM agent uses. What makes the swarm special is **durability** — the runtime persists state at key points so execution survives failures:

- **Regular tool calls**: executed inline within a single workflow step. If the step fails, it retries from the last LLM call.
- **Agent handoff calls**: executed as a **separate durable workflow step** with its own timeout and recovery. State is persisted before and after the handoff.
- **Swarm handoff calls**: parent workflow pauses, child swarm runs independently (see Composition).
- **Between round trips**: session memory is persisted. Service restarts resume from the last completed round.

### Swarm state

```java
record SwarmState(
    Status status,              // RUNNING, PAUSED, COMPLETED, FAILED, STOPPED
    int currentTurn,
    int maxTurns,
    String currentAgentId,      // which agent/handoff is active (null = root)
    List<TurnRecord> turnHistory,
    Optional<String> result,
    Optional<PauseReason> pauseReason
) {}
```

### Built-in tools

The swarm runtime injects these as available tools in the system prompt:

| Built-in tool | LLM invokes when... | Runtime effect |
|---------------|---------------------|----------------|
| `complete(result)` | Goal achieved, explicit result | Terminates successfully |
| `pause(reason, context)` | Needs human decision | Pauses workflow |
| `fail(reason)` | Cannot continue | Terminates with failure |

### Loop strategies (extensibility)

The default loop strategy is the simple agent loop described above. The design accommodates future loop strategies, potentially as an additional override on the swarm class:

```java
// Future v2:
@Override
protected LoopStrategy loopStrategy() {
  return LoopStrategy.adaptive()
      .withEvaluator(EvaluatorAgent.class)
      .withStallThreshold(3)
      .withMaxReplans(2);
}
```

This would allow supporting the AdaptiveLoopWorkflow pattern (outer/inner loops, stall detection, replanning) as a configurable loop strategy rather than requiring a custom Workflow subclass.

---

## Composition (Swarm of Swarms) - Details

### Composing swarms via class references

In the class-based design, composition is expressed through `Handoff.toSwarm(ChildSwarm.class)`:

```java
@Component(id = "activity-booking")
public class ActivityBookingSwarm extends Swarm<String, ActivityBookingResult> {

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toSwarm(ActivityPlannerSwarm.class)
            .withDescription("Plans outdoor activities"),
        Handoff.toSwarm(TicketingSwarm.class)
            .withDescription("Handles ticket reservation and payment"));
  }

  // ...
}
```

Each child swarm (`ActivityPlannerSwarm`, `TicketingSwarm`) is a registered `@Component` with its own instructions, handoffs, tools, and result type. The parent does not need to know the child's internal structure.

### Parent-child lifecycle

When a swarm's LLM requests a handoff to another swarm:

```
Parent swarm running (session: parent-session)
  → LLM calls handoff_to_ticketing("book activity X")
  → Parent stores pending handoff + expected result type in state
  → Parent workflow PAUSES

Child swarm starts (session: NEW child-session)
  → Receives handoff request as initial user message
  → Runs its own loop with own instructions, handoffs, tools
  → May have its own HITL pauses
  → Completes (success or failure)
  → Sends result back to parent swarm (resume command)

Parent workflow RESUMES
  → Receives child result
  → Result fed to parent's LLM as handoff tool result
  → Parent continues its loop
```

### Session isolation

Each swarm has its own session. Data sharing between parent and child is explicit:
- **Parent → child**: Data flows via the handoff request (the LLM's tool call argument)
- **Child → parent**: Data flows via the completion result

This prevents context pollution in deep compositions and keeps each swarm's conversation focused.

**Within a single swarm**: All agent handoffs share the same session in v1. Agent responses accumulate in the session memory, providing conversational context. Future versions may introduce more sophisticated session management within a single swarm — for example, giving individual agents filtered or summarized views of the session rather than the full history, or scoping memory per agent type to reduce noise and context window usage.

### Error handling

Child swarm failure resumes the parent with an error result. The parent's LLM sees the error as a tool result and can reason about it (retry, try different approach, fail explicitly).

### Composition depth

No explicit depth limit in v1. Each level has its own `maxTurns()` which provides bounded execution. Future versions may add explicit depth limits or total cost/token budgets.

---

## LLM-Driven Child Swarm Customization

### The idea

So far, composition has been **developer-defined**: the swarm class declares which child swarms are available as handoffs, and optionally uses `getInput()` to make dynamic choices based on the user's original input. But the most powerful composition pattern is when the **parent swarm's LLM itself** influences the behavior of the child swarm it spawns — choosing not just *which* child swarm to invoke, but *how* it should behave.

This goes beyond `getInput()`-based dynamism. With `getInput()`, the swarm class author writes conditional logic against the original user input. With LLM-driven customization, the parent's orchestrator LLM reasons about the problem at runtime and produces configuration parameters that weren't anticipated by the developer — tweaking instructions, adding or removing agents, and adjusting tools based on context the LLM has gathered during its conversation.

### Canonical example: Customer support triage

A triage swarm analyzes an incoming customer request — identifying the customer's tier, the problem domain, and the urgency. Based on this analysis, the LLM decides:

1. **Which** specialized child swarm should handle the request (billing, technical, account management)
2. **How** that child swarm should behave — different instructions, agents, and tools depending on what the LLM learned during triage

```
Triage swarm (LLM analyzes customer request)
  → LLM gathers context: customer is VIP, issue is billing dispute, high urgency
  → LLM calls handoff_to_billing_support_swarm with:
      - The customer request
      - Customization: "This is a VIP customer — use the premium resolution tools,
        include the account manager agent, escalation threshold is lower"
```

The key insight is that the customization parameters come from the **LLM's reasoning**, not from developer-written conditional logic. The triage LLM might decide that a particular combination of agents and tools is appropriate based on patterns it recognizes in the customer's message — combinations the developer never explicitly coded for.

### How it works

When the parent swarm's LLM calls a swarm handoff, the tool call can include a structured `customization` parameter alongside the request. The swarm runtime merges these overrides with the child swarm's class-defined configuration:

```
Available handoffs:
- handoff_to_billing_support_swarm(request: string, customization: object):
    "Handles billing inquiries, disputes, and payment issues"
    customization schema:
      - additional_instructions: string (appended to child's instructions)
      - additional_agents: list of agent IDs to include
      - additional_tools: list of tool names to enable
      - max_turns_override: int
```

The child swarm class provides the base configuration (instructions, handoffs, tools). The LLM-provided customization **extends** it — it can add agents, append instructions, or enable tools, but cannot remove the child swarm's core configuration. This keeps the child swarm's safety invariants intact while allowing the parent LLM to tailor its behavior.

### Example: VIP vs standard customer paths

```java
@Component(id = "customer-support-triage")
public class CustomerSupportTriageSwarm extends Swarm<String, SupportResolution> {

  @Override
  protected String instructions() {
    return """
        You are a customer support triage system. Analyze the incoming request to determine:
        1. The customer's tier (standard, premium, VIP) — check with the CRM agent
        2. The problem domain (billing, technical, account, general)
        3. The urgency level

        Based on your analysis, hand off to the appropriate support swarm.
        When handing off, customize the child swarm based on what you learned:

        For VIP customers:
        - Add instructions to prioritize retention and offer goodwill gestures
        - Include the account-manager agent for personalized follow-up
        - Include the premium-resolution-tools for enhanced capabilities

        For billing disputes over $500:
        - Add instructions to escalate if not resolved in 3 turns
        - Include the fraud-check agent

        For technical issues with recent deployments:
        - Add instructions to check the deployment log first
        - Include the infrastructure agent""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent("crm-agent")
            .withDescription("Looks up customer profile, tier, and history"),
        Handoff.toSwarm(BillingSupportSwarm.class)
            .withDescription("Handles billing inquiries, disputes, and payment issues")
            .customizable(),  // Enables LLM-driven customization of this child swarm
        Handoff.toSwarm(TechnicalSupportSwarm.class)
            .withDescription("Handles technical issues and troubleshooting")
            .customizable(),
        Handoff.toSwarm(AccountManagementSwarm.class)
            .withDescription("Handles account changes, upgrades, and closures")
            .customizable());
  }

  // ...
}
```

The `.customizable()` marker on a swarm handoff tells the runtime to expose the customization schema in the tool definition, allowing the parent LLM to provide overrides when calling the handoff.

### What the parent LLM produces

When the triage LLM decides to hand off to the billing swarm for a VIP customer:

```json
{
  "tool_call": "handoff_to_billing_support_swarm",
  "arguments": {
    "request": "Customer disputes charge of $750 on invoice #4521. Claims service was not delivered.",
    "customization": {
      "additional_instructions": "This is a VIP customer (tier: platinum, 8 years). Prioritize retention. Offer goodwill credit up to $200 if dispute cannot be fully resolved. Tone should be empathetic and premium.",
      "additional_agents": ["account-manager-agent", "fraud-check-agent"],
      "additional_tools": ["premium-resolution-tools"],
      "max_turns_override": 15
    }
  }
}
```

The `BillingSupportSwarm` class defines the base behavior (its own instructions, standard agents, standard tools). The runtime merges the LLM-provided customization:
- `additional_instructions` are appended to the child's `instructions()`
- `additional_agents` are added to the child's `handoffs()`
- `additional_tools` are added to the child's `tools()`
- `max_turns_override` replaces the child's `maxTurns()`

### Relationship to `withParameters()`

This mechanism is conceptually similar to the call-site `withParameters(SwarmParams)` override, but with a key difference:

| Aspect | `withParameters()` | LLM-driven customization |
|--------|-------------------|--------------------------|
| **Who decides** | Developer at the call site | Parent swarm's LLM at runtime |
| **When decided** | Code time / deploy time | Runtime, based on LLM reasoning |
| **Scope** | Full replacement of config | Additive extensions to base config |
| **Use case** | Known variations (e.g., "add fact-checker") | Emergent variations based on context |

`withParameters()` is a **developer override** — the developer knows at code time what customization they want. LLM-driven customization is an **AI override** — the parent LLM discovers at runtime what customization is appropriate.

---

## Swarm Discovery via SwarmRegistry

### The idea

Just as the existing `AgentRegistry` allows agents to be discovered at runtime rather than referenced statically, a **SwarmRegistry** would allow swarms to discover and delegate to other swarms dynamically. A planner or triage swarm would analyze the goal, then use the registry to find relevant swarms for sub-tasks — without the developer needing to enumerate all possible child swarms in the `handoffs()` method.

### How it works

The `SwarmRegistry` is a tool available to the orchestrator LLM. It provides a searchable catalog of registered swarm classes with their metadata: component ID, description, input type, result type, and the agents/swarms they orchestrate.

```java
@Component(id = "task-planner")
public class TaskPlannerSwarm extends Swarm<String, TaskResult> {

  @Override
  protected String instructions() {
    return """
        You are a general-purpose task planner. Analyze the user's goal and break it
        into sub-tasks. For each sub-task, use the swarm_registry tool to find a
        suitable swarm that can handle it. If multiple swarms match, choose the most
        specific one.

        Once you've identified the right swarms, hand off each sub-task to its
        corresponding swarm. Collect the results and compile them into a final answer.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    // No static handoffs — all discovered at runtime via the registry
    return List.of();
  }

  @Override
  protected List<Object> tools() {
    return List.of(new SwarmRegistry());
  }

  // ...
}
```

### What the LLM sees

The `SwarmRegistry` exposes itself as a function tool:

```
Available tools:
- swarm_registry_search(query: string, capabilities: list[string]):
    "Search for registered swarms that match a description or required capabilities.
     Returns a list of swarm entries with id, description, input/result types,
     and available handoffs."

- swarm_registry_handoff(swarm_id: string, request: string, customization: object):
    "Hand off a task to a dynamically discovered swarm. The swarm_id must come
     from a previous swarm_registry_search result."
```

### Example flow

```
User: "Plan a corporate team-building event in Tokyo for 20 people, budget $5000"

TaskPlannerSwarm LLM:
  1. Calls swarm_registry_search("venue booking and event planning in Tokyo")
     → Returns: EventPlanningSwarm, VenueBookingSwarm, CateringSwarm

  2. Calls swarm_registry_search("travel and logistics coordination")
     → Returns: TravelCoordinationSwarm, TransportSwarm

  3. Calls swarm_registry_search("budget management and cost tracking")
     → Returns: BudgetSwarm

  4. Hands off sub-tasks to discovered swarms:
     → swarm_registry_handoff("event-planning", "Plan team-building activities...")
     → swarm_registry_handoff("venue-booking", "Find venues in Tokyo for 20...")
     → swarm_registry_handoff("budget", "Track expenses against $5000 budget...")
```

### Combining discovery with customization

SwarmRegistry discovery and LLM-driven customization compose naturally. The planner swarm discovers child swarms via the registry and customizes them based on what it learned during analysis:

```
TaskPlannerSwarm LLM:
  1. Analyzes the request → corporate event, Japan, VIP attendees
  2. Discovers VenueBookingSwarm via registry
  3. Calls swarm_registry_handoff("venue-booking",
       request: "Book venue in Tokyo for 20 corporate attendees",
       customization: {
         additional_instructions: "Attendees include C-level executives.
           Prioritize premium venues with private dining. Must have
           English-speaking staff.",
         additional_agents: ["concierge-agent"]
       })
```

The planner didn't know at design time that it would need a concierge agent or premium venue filtering — this emerged from the LLM's analysis of the specific request.

### Registry metadata

Each registered swarm contributes metadata to the registry automatically from its class definition:

```java
record SwarmRegistryEntry(
    String componentId,          // From @Component(id)
    String description,          // From @Component or class javadoc
    Class<?> inputType,          // From Swarm<A, B> type parameter A
    Class<?> resultType,         // From Swarm<A, B> type parameter B
    List<String> capabilities,   // Derived from handoffs and tools
    boolean customizable         // Whether it accepts LLM-driven customization
) {}
```

**Questions**: Should the registry be populated automatically from all `@Component` swarm classes, or opt-in? Should it support tagging/categorization beyond what's in `@Component`? How does the registry interact with access control — can any swarm discover and invoke any other swarm? Should registry search use semantic similarity (embedding-based) or keyword matching?

---

## Example Swarm Gallery

### Simple Triage

Routes requests to the appropriate agent based on language detection — no custom triage agent needed:

```java
@Component(id = "triage")
public class TriageSwarm extends Swarm<String, String> {

  @Override
  protected String instructions() {
    return "Handoff to the appropriate agent based on the language of the request.";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent(SpanishAgent.class),
        Handoff.toAgent(EnglishAgent.class));
  }

  @Override
  protected Class<String> resultType() { return String.class; }

  @Override
  protected int maxTurns() { return 3; }
}
```

### Writer-Critic Feedback Loop

Iterative refinement between a writer and a critic:

```java
@Component(id = "content-refinement")
public class ContentRefinementSwarm extends Swarm<String, ContentQuality> {

  @Override
  protected String instructions() {
    return """
        You coordinate a content creation and refinement process.

        Follow this loop:
        1. Hand off to the writer agent with the user's brief
        2. Hand off to the critic agent with the writer's output
        3. If the critic responds with APPROVED, compile the final result
        4. If the critic has feedback, hand off to the writer again
        5. Repeat steps 2-4

        If you reach the turn limit without approval, use the best version so far.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent(WriterAgent.class),
        Handoff.toAgent(CriticAgent.class));
  }

  @Override
  protected Class<ContentQuality> resultType() { return ContentQuality.class; }

  @Override
  protected int maxTurns() { return 8; }
}
```

### Multi-Phase Content Pipeline with HITL

Research → write → edit → evaluate → human review, with iterative refinement loops:

```java
@Component(id = "content-creation")
public class ContentCreationSwarm extends Swarm<String, ContentResult> {

  @Override
  protected String instructions() {
    return """
        You are a content production orchestrator.

        PHASE 1 — Research: Break the topic into sub-topics, hand off to the researcher.
        PHASE 2 — Writing: Hand off research to the writer.
        PHASE 3 — Editing: Hand off the draft to the editor.
        PHASE 4 — Evaluation: Hand off to the evaluator. If isComplete=false and
                  < 3 cycles: loop back through research → write → edit → evaluate.
        PHASE 5 — Human review: PAUSE the swarm with the final content.
                  On resume: if approved, compile result; if changes needed, loop back.""";
  }

  @Override
  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent(ResearcherAgent.class)
            .withDescription("Researches a specific sub-topic using web search"),
        Handoff.toAgent(WriterAgent.class)
            .withDescription("Writes content based on research and style"),
        Handoff.toAgent(EditorAgent.class)
            .withDescription("Polishes a draft for grammar and readability"),
        Handoff.toAgent(EvaluatorAgent.class)
            .withDescription("Evaluates content quality and completeness"));
  }

  @Override
  protected Class<ContentResult> resultType() { return ContentResult.class; }

  @Override
  protected int maxTurns() { return 25; }
}
```

### Dynamic Agent Discovery

Handoffs populated from a discovery mechanism rather than hardcoded:

```java
@Component(id = "records-search")
public class RecordsSearchSwarm extends Swarm<String, UCCSearchResult> {

  @Override
  protected List<Handoff> handoffs() {
    // Could query an agent directory, a view, or registry
    return discoverSearchAgents();
  }

  private List<Handoff> discoverSearchAgents() {
    return List.of(
        Handoff.toAgent("county-records-agent")
            .withDescription("Searches county clerk UCC filing databases"),
        Handoff.toAgent("state-sos-agent")
            .withDescription("Searches Secretary of State UCC filing records"),
        Handoff.toAgent("federal-records-agent")
            .withDescription("Searches federal lien and filing databases"));
  }

  // ...
}
```

---

## Future Topics (TODO)

Topics we're aware of but haven't explored in depth yet. Each needs its own design discussion.

### Parallel Execution Within a Swarm

Executing multiple agent handoffs concurrently within a single swarm turn. The current design assumes sequential handoffs. But an LLM might request multiple tool calls in a single response (e.g., "get weather AND check calendar simultaneously").

**Questions**: Should the runtime automatically parallelize? How does parallel execution interact with durable state? How are results collected and fed back? Error handling if one parallel handoff fails?

### Parallel Child Swarm Spawning

A parent swarm spawning multiple child swarms concurrently (e.g., "book hotel AND book flight at the same time"). The parent would need to track all children.

**Questions**: Does the parent wait for ALL children? What happens if one fails but others succeed? Should this be a "fan-out / fan-in" pattern at the workflow level?

### Advanced Loop Strategies

Configurable loop behaviors beyond the simple agent loop. The AdaptiveLoopWorkflow (MagenticOne pattern) implements: outer/inner loops, separate evaluator agent, stall detection, automatic replanning.

**Questions**: `LoopStrategy.simple()` vs `LoopStrategy.adaptive(config)` — how does config look? Should the evaluator be an agent or a Java function? Automatic vs evaluator-based stall detection? Should this be an override method on the swarm class or a builder parameter?

### Inline Agents

Defining lightweight agents as configuration within `Handoff.inline()` instead of requiring `@Component` classes.

**Questions**: Are inline agents just "instructions + tools" creating a temporary LLM interaction? Do they get their own system prompt? How do they differ from just adding more tools to the swarm itself?

### Observability and Intent Logging

Intent-based logging as a first-class swarm feature. At each turn, the runtime would emit structured records: which agent, what task, how it interpreted it, what action chosen and why.

**Questions**: Intent log format and storage? Similar to Agent interaction log? Integration with OpenTelemetry tracing? Cost tracking aggregated across all LLM calls? Console/CLI visualization? How does observability work for composed swarms?

### Testing Model

How developers test swarm configurations.

**Questions**: Can `TestModelProvider` mock the swarm's own LLM interactions? How to test a specific handoff sequence? Integration tests with real swarms? Should there be a swarm-specific test kit?

### Cost Controls and Guardrails

Limiting swarm execution costs and enforcing policies.

**Questions**: Token budgets? Cost caps (dollar limits)? Guardrail hooks (pre/post-LLM validation)? Rate limiting on handoffs? Composition depth limits?

### Context Window Management

Managing session memory for long-running swarms.

**Questions**: How to prevent context window exhaustion as turns accumulate? Summarization strategies? Selective memory per agent type? Integration with existing `MemoryProvider`?

### Exposing a Swarm as MCP Server or A2A Server

A swarm has a natural mapping to both the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-11-25) and the [Agent2Agent (A2A) protocol](https://a2a-protocol.org/latest/specification/). Exposing a swarm via these protocols would let external LLMs and agents interact with it as a remote capability — without knowing it's a swarm internally.

#### MCP Server

An MCP server exposes **tools**, **resources**, and **prompts** to LLM clients. A swarm maps to this as:

| Swarm concept | MCP concept | Notes |
|---|---|---|
| `run(input)` | **Tool** | A tool named after the swarm (e.g., `activity_planner`). Input schema derived from the swarm's input type `A`. |
| `getResult()` | **Resource** | `swarm://{swarmId}/result` — pollable resource for the result. |
| `resume(message)` | **Tool** | A follow-up tool for HITL swarms, invoked when the MCP client needs to provide input. |
| `events()` | **MCP Tasks** (experimental) | The 2025-11-25 spec introduces async Tasks — maps directly to the swarm's fire-and-retrieve pattern. |
| `instructions()` | **Prompt** | The swarm's instructions could be exposed as an MCP prompt template. |
| `resultType()` | Tool output schema | The JSON schema of the result type becomes the tool's output schema. |

**What's needed**:
- An annotation or configuration to mark a swarm as MCP-exposed
- Automatic generation of MCP tool definitions from swarm metadata: name from `@Component(id)`, description from `@Component` annotations, input schema from type parameter `A`, output schema from `resultType()`
- HTTP/SSE transport endpoint for MCP (Akka HTTP endpoint serving the MCP protocol)
- Mapping swarm lifecycle to MCP Tasks for long-running swarms
- Authentication integration — MCP 2025-11-25 uses OAuth 2.1

#### A2A Server

The A2A protocol is designed specifically for agent-to-agent interaction. A swarm maps even more naturally:

| Swarm concept | A2A concept | Notes |
|---|---|---|
| Swarm class + `@Component` | **Agent Card** (`/.well-known/agent.json`) | Name, description, skills, endpoint, auth. The `@Component` annotation metadata, input/result type schemas, and handoffs populate the card. |
| `run(input)` | **SendMessage** / **SendStreamingMessage** | Creates a Task. The `swarmId` maps to A2A `taskId`, and the session maps to `contextId`. |
| `getResult()` | **GetTask** | Polls task status and retrieves artifacts. |
| `events()` | **SSE stream** / **SubscribeToTask** | Real-time `TaskStatusUpdateEvent` and `TaskArtifactUpdateEvent` map directly to `SwarmEvent` variants. |
| `SwarmResult` states | **Task lifecycle states** | `Running` → `working`, `Paused` → `input_required`, `Completed` → `completed`, `Failed` → `failed`, `Stopped` → `canceled`. |
| `resume(message)` | **Multi-turn SendMessage** | Client sends additional message referencing same `taskId` when task is in `input_required` state. This is exactly the HITL pattern. |
| `resultType()` output | **Artifacts** | The swarm result becomes an A2A Artifact with structured data parts. |
| Swarm composition | **Agent-to-agent delegation** | A parent swarm's handoff to a child swarm is itself an A2A interaction — the parent is a client of the child's A2A server. |

**What's needed**:
- Automatic Agent Card generation from swarm class metadata (`@Component(id)` → name, class annotations → description, handoffs → skills)
- A2A HTTP endpoint implementing the protocol (SendMessage, GetTask, CancelTask, SubscribeToTask)
- Mapping between `SwarmResult` ADT states and A2A task lifecycle states
- SSE streaming bridge from `SwarmEvent` to A2A `TaskStatusUpdateEvent`/`TaskArtifactUpdateEvent`
- Authentication support (API keys, OAuth2, mTLS as declared in the Agent Card)

#### Key observation

Both protocols benefit from the same swarm metadata: the input type `A` provides the input schema, `resultType()` provides the output schema, `@Component` annotations provide the external-facing description, and the component ID provides the name.

The swarm's fire-and-retrieve pattern (`run` returns void, poll `getResult`) and streaming events align particularly well with A2A's task lifecycle and MCP's experimental Tasks primitive. The HITL pause/resume pattern maps directly to A2A's `input_required` state and multi-turn messaging.

**Questions**: Should this be opt-in per swarm or automatic for all swarms? Can a single swarm be exposed via both protocols simultaneously? How does authentication flow through composed swarms (parent calls child via A2A)? Should the Agent Card / MCP tool definition be generated at build time or served dynamically?

---

## Summary

The Swarm component:

1. **Is a user-defined class** — extends `Swarm<A, B>` with `@Component` annotation. The developer declares behavior by overriding `instructions()`, `handoffs()`, `resultType()`, and optionally `tools()` and `maxTurns()`. The runtime provides the agent loop and built-in operations.
2. **Is fully typed** — two type parameters (`A` for input, `B` for result) flow through the `SwarmClient<A, B>`, `SwarmResult<B>`, and `run(A)`. No casts needed.
3. **Supports dynamic configuration** — override methods can use `getInput()` to inspect the runtime input and dynamically choose handoffs, tools, instructions, and max turns.
4. **Has a dedicated client API** — `componentClient.forSwarm(SwarmClass.class, swarmId)` returns a typed `SwarmClient` with `run`/`resume`/`stop`/`getResult`/`events` methods, plus `withParameters()` for call-site overrides.
5. **Uses handoffs as function tools** — exposed to the LLM via `Handoff.toAgent()` and `Handoff.toSwarm()` (by class or ID), managed by the swarm runtime with durable execution.
6. **Supports composition** — `Handoff.toSwarm(ChildSwarm.class)` enables swarm-of-swarms. Parent pauses when spawning child, child has its own session, result flows back via resume.
7. **Supports LLM-driven child customization** — parent swarm's LLM can customize child swarm behavior (instructions, agents, tools) at runtime based on its reasoning, via `.customizable()` handoffs. This enables emergent specialization the developer didn't anticipate.
8. **Supports swarm discovery** — `SwarmRegistry` allows swarms to discover and delegate to other swarms dynamically, enabling open-ended task decomposition without static handoff enumeration.
9. **Is async** — fire-and-retrieve with streaming notifications for progress.
