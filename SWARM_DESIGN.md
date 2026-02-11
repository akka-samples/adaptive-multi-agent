# Akka SDK Swarm Component - Design Document

## Executive Summary

This document proposes a **Swarm** component for the Akka SDK that provides on-demand, multi-agent orchestration with automatic looping, handoffs, and termination. A Swarm blends concepts from existing **Workflow** (orchestration, state management, pause/resume) and **Agent** (LLM interaction, tools, session memory) components to create a higher-level abstraction for agentic collaboration.

**Key Design Principle**: Swarms are **on-demand orchestrators** that coordinate multiple agents to achieve a specific goal through iterative loops and handoffs, then complete.

---

## Component Model

### Swarm is a pre-built Workflow

A Swarm is a **framework-provided Workflow** with a fixed step graph (the agent loop), configured at runtime via `SwarmParams` instead of at compile time via Java code.

**No user class needed.** Unlike Agent/Workflow/Entity where users extend a base class, Swarm is invoked directly with configuration. This fits because:
- Swarms are transient/on-demand ("agentic map-reduce"), not long-lived services
- The "logic" lives in the prompt instructions, not Java code
- Reduces boilerplate to a single invocation call
- The orchestration loop is generic - same step graph for all swarms

**Key conceptual shift**: Today, the developer writes orchestration logic (Workflow steps decide which agent to call). With Swarm, the **LLM IS the orchestrator** - it decides which handoffs to invoke based on instructions, and the runtime manages the durable execution loop.

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
- Can reference existing `@Component` agents
- Enable dynamic agent selection at runtime
- Managed by the swarm to provide durable execution

### Loop Termination Conditions

A swarm loop terminates when:

**Success Cases:**
1. LLM returns response with **no tool calls or handoffs**
2. LLM output **conforms to expected type** (strongly typed success)
3. **Explicit termination** via special tool (e.g., `complete()`)

**Failure Cases:**
1. **Max iterations exceeded** (runaway prevention)
2. **Fatal error** during LLM or tool execution
3. **Guardrail violation** (policy enforcement)
4. **Explicit stop** via pause/resume mechanism

**Pause Case:**
1. **Human-in-the-loop** approval needed (business logic pause)

### Composition (Swarm of Swarms)

Swarms should support **recursive composition**:
- A swarm's handoff can point to another swarm
- Inner swarms have their own loops and termination conditions
- Parent swarm waits for child swarms to complete
- Enables reuse of specialized swarms (e.g., ticket booking swarm called by activity planner swarm)

---

### API Design

#### Basic Swarm Invocation

```java
// Example: Activity planner swarm
componentClient
    .forSwarm(swarmId)
    .method(Swarm::run)
    .invoke(SwarmParams.builder()
        .userMessage("Suggest outdoor activities for this weekend")
        .instructions("""
            Using available tools and handoffs, gather information about:
            - Weather forecast for the requested dates
            - User's calendar availability
            - Current allergen levels

            Recommend activities that are safe and enjoyable.
            If allergen levels are too high on a target day,
            exclude that day from recommendations.
            """)
        .resultAs(ActivityRecommendation.class)  // Termination: type match
        .handoffs(
            Handoff.toAgent("weather-agent"),
            Handoff.toAgent("calendar-agent"),
            Handoff.toAgent("allergen-agent")
        )
        .maxTurns(10)  // Runaway prevention
        .build());
```

**Key Features:**
- Uses familiar `componentClient.forSwarm(swarmId)` pattern
- `swarmId` is both the unique instance identifier and the session ID for conversational memory shared across agents within the swarm
- `Swarm::run` is a special method on a built-in component, returns void but throws if the swarm could not be started
- `SwarmParams` encapsulates all configuration including the user message and expected response type
- `resultAs(Class)` defines success termination condition
- `maxTurns` prevents runaway loops

#### Component Metadata for Observability

Optionally, component metadata can be set on the swarm client for observability. These correspond to the `@Component` annotation fields on regular Akka components:

```java
componentClient
    .forSwarm(swarmId)
    .withComponentId("activity-planner")
    .withComponentName("Activity Planner")
    .withComponentDescription("Plans outdoor activities based on weather, calendar, and allergens")
    .method(Swarm::run)
    .invoke(params);
```

Since swarms are configured at runtime (not via class annotations), this metadata is optional and set fluently on the client. It enables filtering, grouping, and labeling swarm instances in monitoring and tracing systems.

#### Retrieving the Result

Since `Swarm::run` is returning void, the final result must be retrieved separately. `getResult` returns a `SwarmResult` — a sealed interface ADT that can be consumed via pattern matching.

Alternatively, `Swarm::run` could return the final result, but we would still need a separate `getResult`, because a swarm might be rather long lived, and outlives the client, and then it should still be possible to ask it for result (or notification stream).

```java
// Poll for the result
SwarmResult result = componentClient
    .forSwarm(swarmId)
    .method(Swarm::getResult)
    .invoke();

// Pattern match on the result
switch (result) {
    case SwarmResult.Completed c -> {
        ActivityRecommendation recommendation = c.resultAs(ActivityRecommendation.class);
        // use recommendation
    }
    case SwarmResult.Paused p -> {
        // awaiting human input
        // p.reason().message(), p.currentTurn()
    }
    case SwarmResult.Running r -> {
        // still in progress
        // r.currentAgent(), r.currentTurn(), r.maxTurns()
    }
    case SwarmResult.Failed f -> {
        // handle failure: f.reason()
    }
    case SwarmResult.Stopped s -> {
        // externally stopped: s.reason()
    }
}
```

**SwarmResult model (sealed ADT):**
```java
sealed interface SwarmResult {
    record Running(int currentTurn, int maxTurns,
                   Optional<String> currentAgent,
                   Optional<String> currentChildSwarm) implements SwarmResult {}
    record Paused(PauseReason reason, int currentTurn, int maxTurns) implements SwarmResult {}
    record Completed(Object result) implements SwarmResult {
        <T> T resultAs(Class<T> type) { return type.cast(result); }
    }
    record Failed(String reason) implements SwarmResult {}
    record Stopped(String reason) implements SwarmResult {}
}
```

Each variant carries only the fields relevant to that state — no optional fields needed. The `resultAs()` typed accessor is only available on `Completed`.

#### Notification Stream

Subscribe to a real-time stream of swarm events to track progress, agent handoffs, and completion.

```java
// Stream of swarm events (SSE or similar)
Source<SwarmEvent, NotUsed> events = componentClient
    .forSwarm(swarmId)
    .streamMethod(Swarm::events)
    .source();

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
// Pattern 1: Reference existing agent by ID
var handoff1 = Handoff.toAgent("weather-agent");

// Pattern 2: Reference agent class
var handoff2 = Handoff.toAgent(CustomRaterAgent.class);

// Pattern 3: Inline swarm definition (composition!)
var handoff3 = Handoff.toSwarm(
    SwarmParams.builder()
        .instructions("Book tickets for the recommended activity...")
        .handoffs(
            Handoff.toAgent("ticketing-agent"),
            Handoff.toAgent("payment-agent")
        )
        .resultAs(BookingConfirmation.class)
        .maxTurns(5)
        .build()
);

```

#### What the LLM sees

Handoffs are **metadata** that get compiled into the system prompt as available function tools. Agent metadata from `@Component(name, description)` annotations is used to generate tool descriptions.

```
Available handoffs:
- handoff_to_weather_agent(request: string): "Weather Agent - provides weather
  information, forecasts, and conditions"
- handoff_to_activity_agent(request: string): "Activity Agent - suggests real-world
  activities like sports, games, attractions"
- handoff_to_ticket_booking_swarm(request: string): "Ticket Booking - books tickets
  for activities. Handles ticketing and payment."
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

#### Tools in Swarms

```java
// Tools are passed just like in regular agents
componentClient
    .forSwarm(swarmId)
    .method(Swarm::run)
    .invoke(SwarmParams.builder()
        .userMessage("Re-rate policy #12345")
        .instructions("...")
        .resultAs(PolicyRating.class)
        .tools(policyService, ratingEngine, notificationService)
        .handoffs(
            Handoff.toAgent("records-agent"),
            Handoff.toAgent("compliance-agent")
        )
        .build());
```

**Tool vs Handoff Distinction:**
- **Tools**: Functions that return data to the LLM (synchronous, deterministic)
- **Handoffs**: Delegation to another agent/swarm (may involve loops, LLM calls)

Internally, handoffs are **special tools** with naming conventions like `handoff_to_{agentId}`.

#### Pause, Resume, and Stop

```java
// Pause a running swarm (emergency/debugging)
componentClient
    .forSwarm(swarmId)
    .method(Swarm::pause)
    .invoke("Emergency stop for debugging");

// Resume a paused swarm
componentClient
    .forSwarm(swarmId)
    .method(Swarm::resume)
    .invoke("Continue with approval granted");

// Stop a swarm (terminal, cannot resume)
componentClient
    .forSwarm(swarmId)
    .method(Swarm::stop)
    .invoke("User cancelled operation");

// Check swarm status
SwarmResult status = componentClient
    .forSwarm(swarmId)
    .method(Swarm::getResult)
    .invoke();
```

**Status Model:**
```java
record SwarmStatus(
    String swarmId,
    State state,          // RUNNING, PAUSED, COMPLETED, FAILED, STOPPED
    int currentTurn,
    int maxTurns,
    Optional<String> currentAgent,  // Which agent is currently active
    Optional<String> currentChildSwarm,  // Which child swarm is currently active
    Optional<PauseReason> pauseReason
) {
    enum State { RUNNING, PAUSED, COMPLETED, FAILED, STOPPED }
}

record PauseReason(
    Type type,           // HITL, EMERGENCY, APPROVAL_NEEDED
    String message,
    Optional<String> pendingApprovalId
) {
    enum Type { HITL, EMERGENCY, APPROVAL_NEEDED }
}
```

#### Human-in-the-Loop Pattern

```java
// Instructions can reference built-in pause behavior
String INSTRUCTIONS = """
Re-rate insurance policies according to current market conditions.

If the new APR differs by more than 0.5% from the current rate,
you must:
1. Use the notification tool to alert the underwriting team
2. Use the pause tool with reason "APPROVAL_NEEDED"
3. Wait for external approval before continuing
""";

// Built-in tools available to all swarms:
// - pause(reason, context): Pause swarm for HITL
// - complete(result): Explicitly complete with result
// - fail(reason): Explicitly fail with error

componentClient
    .forSwarm(swarmId)
    .method(Swarm::run)
    .invoke(SwarmParams.builder()
        .userMessage("Re-rate policy #12345")
        .instructions(INSTRUCTIONS)
        .resultAs(ReratedPolicy.class)
        .tools(notificationService, policyDatabase)
        .handoffs(Handoff.toAgent("compliance-agent"))
        .maxTurns(20)
        .build());

// Later, after human approval:
componentClient
    .forSwarm(swarmId)
    .method(Swarm::resume)
    .invoke("Underwriter approved change. Continue.");
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
| **Response matching `resultAs` type** | Terminate successfully (type-match termination) |
| **Clean text response** (no tool calls) | Terminate (orchestrator is done) |

After each round trip the runtime also checks guards: max rounds exceeded? guardrail violated?

**What counts as a "round"?** One LLM response + execution of its tool calls. `maxTurns` limits the number of these round trips to prevent runaway conversations.

### Durable execution boundaries

The tool-calling loop is the same pattern any LLM agent uses. What makes the swarm special is **durability** - the runtime persists state at key points so execution survives failures:

- **Regular tool calls**: executed inline within a single workflow step. If the step fails, it retries from the last LLM call.
- **Agent handoff calls**: executed as a **separate durable workflow step** with its own timeout and recovery. State is persisted before and after the handoff.
- **Swarm handoff calls**: parent workflow pauses, child swarm runs independently (see Composition).
- **Between round trips**: session memory is persisted. Service restarts resume from the last completed round.

### Swarm state

```java
record SwarmState(
    SwarmParams config,
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

The default loop strategy is the simple agent loop described above. The design accommodates future loop strategies:

```java
SwarmParams.builder()
    .instructions("...")
    .handoffs(...)
    .maxTurns(10)
    // Default: simple agent loop

    // Future v2:
    // .loopStrategy(LoopStrategy.adaptive()
    //     .withEvaluator("evaluator-agent")
    //     .withStallThreshold(3)
    //     .withMaxReplans(2))
    .build()
```

This would allow supporting the AdaptiveLoopWorkflow pattern (outer/inner loops, stall detection, replanning) as a configurable loop strategy rather than requiring a custom Workflow subclass.

---

## Composition (Swarm of Swarms) - Details

### Parent-child lifecycle

When a swarm's LLM requests a handoff to another swarm:

```
Parent swarm running (session: parent-session)
  → LLM calls handoff_to_ticket_booking_swarm("book activity X")
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

**Within a single swarm**: All agent handoffs share the same session in v1. Agent responses accumulate in the session memory, providing conversational context. Future versions may introduce more sophisticated session management within a single swarm - for example, giving individual agents filtered or summarized views of the session rather than the full history, or scoping memory per agent type to reduce noise and context window usage.

### Error handling

Child swarm failure resumes the parent with an error result. The parent's LLM sees the error as a tool result and can reason about it (retry, try different approach, fail explicitly).

### Composition depth

No explicit depth limit in v1. Each level has its own `maxTurns` which provides bounded execution. Future versions may add explicit depth limits or total cost/token budgets.

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

**Questions**: `LoopStrategy.simple()` vs `LoopStrategy.adaptive(config)` - how does config look? Should the evaluator be an agent or a Java function? Automatic vs evaluator-based stall detection?

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

### Agent Discovery and Dynamic Handoffs

Using `AgentRegistry` to dynamically discover available handoff targets.

**Questions**: Accept agent roles instead of specific IDs? Dynamic resolution at start time vs each turn? How does this interact with `@AgentRole` annotations? Security implications?

### Typed Input (Beyond String)

Currently swarms only accept a `String` user message as input. For many use cases, a structured input type would be more natural — e.g., a `ReRatingRequest` record with policy ID, risk factors, and effective date.

This would likely mean adding `inputType(Class<T>)` to `SwarmParams.Builder`, which changes the signature of `Swarm::run` from `run(String)` to `run(T)`. The LLM would receive a serialized representation of the input object as context.

The `inputType` also defines the schema for swarm handoff tool calls. When a parent swarm hands off to a child swarm with a declared `inputType`, the LLM would produce a tool call with a JSON payload conforming to that type — just like it does for `@FunctionTool` parameters. This gives the parent swarm's LLM a structured contract for what data the child swarm expects, rather than passing unstructured text.

**Questions**: How does a structured input interact with the system prompt — is it injected as JSON? Should the builder enforce that `userMessage` and `inputType` are mutually exclusive, or can they coexist (structured data + free-text instructions)? For the class-based design, should `Swarm` have two type parameters `Swarm<I, R>` (input + result)?

### Exposing a Swarm as MCP Server or A2A Server

A swarm has a natural mapping to both the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-11-25) and the [Agent2Agent (A2A) protocol](https://a2a-protocol.org/latest/specification/). Exposing a swarm via these protocols would let external LLMs and agents interact with it as a remote capability — without knowing it's a swarm internally.

#### MCP Server

An MCP server exposes **tools**, **resources**, and **prompts** to LLM clients. A swarm maps to this as:

| Swarm concept | MCP concept | Notes |
|---|---|---|
| `run(userMessage)` | **Tool** | A tool named after the swarm (e.g., `activity_planner`). Input schema derived from `inputType` (or string). |
| `getResult(swarmId)` | **Resource** | `swarm://{swarmId}/result` — pollable resource for the result. |
| `resume(swarmId, message)` | **Tool** | A follow-up tool for HITL swarms, invoked when the MCP client needs to provide input. |
| `events()` | **MCP Tasks** (experimental) | The 2025-11-25 spec introduces async Tasks — any request can return a task handle for "call-now, fetch-later". This maps directly to the swarm's fire-and-retrieve pattern. |
| `instructions` | **Prompt** | The swarm's instructions could be exposed as an MCP prompt template, letting the client understand what the swarm does. |
| `resultType` | Tool output schema | The JSON schema of the result type becomes the tool's output schema. |

**What's needed**:
- An annotation or configuration to mark a swarm as MCP-exposed (e.g., `@McpExposed` or a builder option)
- Automatic generation of MCP tool definitions from swarm metadata: name from component ID, description from `withComponentDescription`, input schema from `inputType`, output schema from `resultType`
- HTTP/SSE transport endpoint for MCP (Akka HTTP endpoint serving the MCP protocol)
- Mapping swarm lifecycle to MCP Tasks for long-running swarms
- Authentication integration — MCP 2025-11-25 uses OAuth 2.1

#### A2A Server

The A2A protocol is designed specifically for agent-to-agent interaction. A swarm maps even more naturally:

| Swarm concept | A2A concept | Notes |
|---|---|---|
| Swarm definition | **Agent Card** (`/.well-known/agent.json`) | Name, description, skills, endpoint, auth. The swarm's component metadata (`withComponentId/Name/Description`), `inputType`/`resultType` schemas, and handoffs populate the card. |
| `run(userMessage)` | **SendMessage** / **SendStreamingMessage** | Creates a Task. The `swarmId` maps to A2A `taskId`, and the session maps to `contextId`. |
| `getResult()` | **GetTask** | Polls task status and retrieves artifacts. |
| `events()` | **SSE stream** / **SubscribeToTask** | Real-time `TaskStatusUpdateEvent` and `TaskArtifactUpdateEvent` map directly to `SwarmEvent` variants. |
| `SwarmResult` states | **Task lifecycle states** | `Running` → `working`, `Paused` → `input_required`, `Completed` → `completed`, `Failed` → `failed`, `Stopped` → `canceled`. |
| `resume(message)` | **Multi-turn SendMessage** | Client sends additional message referencing same `taskId` when task is in `input_required` state. This is exactly the HITL pattern. |
| `resultType` output | **Artifacts** | The swarm result becomes an A2A Artifact with structured data parts. |
| Swarm composition | **Agent-to-agent delegation** | A parent swarm's handoff to a child swarm is itself an A2A interaction — the parent is a client of the child's A2A server. |

**What's needed**:
- Automatic Agent Card generation from swarm metadata (component ID → name, `withComponentDescription` → description, handoffs → skills)
- A2A HTTP endpoint implementing the protocol (SendMessage, GetTask, CancelTask, SubscribeToTask)
- Mapping between `SwarmResult` ADT states and A2A task lifecycle states
- SSE streaming bridge from `SwarmEvent` to A2A `TaskStatusUpdateEvent`/`TaskArtifactUpdateEvent`
- Authentication support (API keys, OAuth2, mTLS as declared in the Agent Card)

#### Key observation

Both protocols benefit from the same swarm metadata: `inputType` provides the input schema, `resultType` provides the output schema, `withComponentDescription` provides the external-facing description, and the component ID provides the name. The `Typed Input` feature (above) becomes especially valuable here — it gives both MCP tools and A2A Agent Cards a proper JSON schema for their input contract, rather than just "a string".

The swarm's fire-and-retrieve pattern (`run` returns void, poll `getResult`) and streaming events align particularly well with A2A's task lifecycle and MCP's experimental Tasks primitive. The HITL pause/resume pattern maps directly to A2A's `input_required` state and multi-turn messaging.

**Questions**: Should this be opt-in per swarm or automatic for all swarms? Can a single swarm be exposed via both protocols simultaneously? How does authentication flow through composed swarms (parent calls child via A2A)? Should the Agent Card / MCP tool definition be generated at build time or served dynamically?

---

## Summary

The Swarm component:

1. **Is a pre-built Workflow** - fixed step graph implementing the agent loop, configured at runtime via `SwarmParams`. No user class needed.
2. **Has a dedicated API** - `componentClient.forSwarm(swarmId)` with run/pause/resume/stop/getResult, plus optional observability metadata via `withComponentId/Name/Description`
3. **Uses handoffs as function tools** - exposed to the LLM, managed by the swarm workflow with durable execution
4. **Starts with a simple loop** - LLM-driven decisions with `maxTurns` guard. Extensible to adaptive strategies.
5. **Supports composition** - parent pauses when spawning child swarm, child has its own session, result flows back via resume
6. **Is async** - fire-and-retrieve with streaming notifications for progress

