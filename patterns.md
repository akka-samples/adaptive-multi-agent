# Multi-Agent Workflow Patterns Overview

This document describes the two workflow patterns for orchestrating multi-agent systems: **AdaptiveLoopWorkflow** and **SequentialPlanWorkflow**.

---

## Pattern Comparison

| Aspect | AdaptiveLoopWorkflow | SequentialPlanWorkflow |
|--------|---------------------|------------------------|
| **Execution Flow** | Dynamic, adapts based on progress | Predetermined, linear or parallel |
| **Planning** | Initial plan + replanning on stalls | Single upfront plan |
| **Progress Evaluation** | After every agent response | None (executes full plan) |
| **Stall Detection** | Yes, with automatic recovery | No |
| **Replanning** | Automatic when stalled | No |
| **Complexity** | Higher (adaptive behavior) | Lower (simple execution) |
| **Best For** | Complex, unpredictable tasks | Well-defined, predictable tasks |

---

## AdaptiveLoopWorkflow

### Purpose

AdaptiveLoopWorkflow implements the **adaptive multi-agent orchestration pattern**. It enables workflows to dynamically coordinate multiple AI agents, continuously evaluate their progress, and adapt execution strategy in response to how well agents are performing.

### Core Concept

Unlike traditional sequential workflows, AdaptiveLoopWorkflow implements a **two-phase loop pattern**:

1. **Outer Loop (Planning Phase)**: Gathers context and creates an execution plan
2. **Inner Loop (Execution Phase)**: Executes agents, evaluates progress after each response, and dynamically decides what to do next

The key innovation is **continuous re-evaluation**: after each agent executes, an orchestrator evaluates whether the task is complete, progress is being made, or if the workflow is stalled—enabling adaptive behavior.

### Execution Flow

```
START
  ↓
gatherFacts() → Collect context about the task
  ↓
createPlan() → Decide initial approach
  ↓
INNER LOOP (repeat up to maxTurns):
  ↓
  evaluateProgress(turn)
    ↓
    ├─→ COMPLETE? → summarize() → END
    ├─→ AWAITING APPROVAL? → PAUSE (HITL) → resume on approval
    ├─→ STALLED? (3x) → replan → OUTER LOOP
    └─→ CONTINUE → executeAgent() → back to INNER LOOP
```

### Key Capabilities

**1. Adaptive Execution**
- Re-evaluates progress after every agent response
- Makes dynamic decisions: continue with next agent, complete the task, or detect problems
- No predetermined execution order—adapts based on actual progress

**2. Intelligent Stall Detection & Recovery**
- Detects when agents are stuck in loops or not making progress
- Automatically triggers replanning when stall threshold is reached (default: 3 consecutive stalls)
- Configurable thresholds for consecutive stalls before intervention

**3. Automatic Replanning**
- Re-enters the planning phase when agents get stuck
- Updates facts and creates a new execution plan
- Bounded attempts to prevent infinite replanning loops (default: 2 max replans)

**4. Parallel Agent Execution**
- Can execute multiple agents concurrently
- Orchestrator decides which agents to run in parallel via `ProgressEvaluation.continueWith(PlanStep.Parallel)`
- State updates applied sequentially to avoid race conditions

**5. Human-in-the-Loop (HITL) Support**
- Can pause workflow for human approval at any decision point
- Orchestrator returns `ProgressEvaluation.awaitingApproval()` when approval needed
- Validates approvals via unique IDs (idempotency)
- Tracks all HITL decisions in audit trail
- Use cases: budget approvals, risk validation, final answer review

**6. Durable State Management**
- Full workflow state persists across pauses and failures
- Composition-based state model via `WithAdaptiveLoopState`
- Separates orchestration state from domain-specific state
- Complete execution history in message logs

### Required Implementations

When extending `AdaptiveLoopWorkflow`, you must implement:

- **gatherFacts()** - Collect initial facts for planning
- **createPlan()** - Create execution plan based on facts
- **evaluateProgress(int turn)** - Evaluate progress and decide next action:
  - `ProgressEvaluation.complete(reason)` - Task satisfied
  - `ProgressEvaluation.continueWith(agentId, instruction)` - Execute next agent
  - `ProgressEvaluation.stalled(agentId, instruction, reason)` - Not making progress
  - `ProgressEvaluation.awaitingApproval(agentId, instruction, context)` - Pause for human approval
- **executeAgent(String agentId, String instruction)** - Execute agent and update state
- **summarize()** - Generate final answer from agent responses
- **handleFailure(String reason)** - Handle workflow failure

### Configuration Options

Override these methods to customize behavior:

- **maxTurns()** - Maximum turns in inner loop (default: 15)
- **stallThreshold()** - Consecutive stalls before replanning (default: 3)
- **maxReplans()** - Maximum replan attempts (default: 2)
- **updateFacts()** - Update facts during replanning (default: calls `gatherFacts()`)
- **updatePlan()** - Update plan during replanning (default: calls `createPlan()`)

### What Problems Does It Solve?

**1. Complex Multi-Agent Coordination**
- When you need multiple specialized agents (web search, code execution, analysis, etc.)
- When agent order depends on previous results
- When you can't predict the execution path in advance

**2. Reliability & Recovery**
- Agents can fail, get stuck, or produce poor results
- Workflow needs to detect problems and recover automatically
- Need bounded retries and graceful degradation

**3. Observability & Control**
- Track exactly what each agent did and why
- Intervene when operations are risky or expensive
- Audit trail of all decisions for compliance

**4. Cost Management**
- Agents make expensive API calls (LLMs, web services)
- Need to track costs and enforce budgets
- Pause for approval before exceeding limits

### Real-World Use Cases

**Research Assistant**
- Orchestrate search, analysis, and summarization agents
- Adapt strategy based on quality of search results
- Replan if initial approach doesn't find good information
- Example: "Research quantum computing advances" → search → evaluate quality → deeper search or analyze → summarize

**Code Generation with Review**
- Planner analyzes requirements
- Multiple code writers work in parallel on different modules
- Reviewer validates output
- Executor runs tests with human approval for risky operations
- Replan if tests fail

**Customer Support Escalation**
- Route to specialized agents based on query type
- Evaluate if customer issue is resolved after each interaction
- Escalate to human if agents are stuck or customer frustrated
- Track costs of each agent call

**Compliance Workflows**
- Multi-step verification process
- Human approval required at critical checkpoints
- Automatic replanning if compliance checks fail
- Complete audit trail for regulatory requirements

---

## SequentialPlanWorkflow

### Purpose

SequentialPlanWorkflow implements a **simple, predictable orchestration pattern** for multi-agent systems. It executes a predetermined plan of agents in sequential or parallel groups, without adaptive evaluation or replanning.

### Core Concept

SequentialPlanWorkflow follows a straightforward **three-phase flow**:

1. **Create Plan**: Define the complete execution plan upfront (which agents, in what order)
2. **Execute Plan**: Execute each step sequentially, or execute parallel groups concurrently
3. **Summarize**: Combine all agent responses into final answer

The plan is **queue-based**: steps are executed in the order they were defined, with optional parallel execution within groups.

### Execution Flow

```
START
  ↓
createPlan() → Define complete execution plan
  ↓
  List<PlanStep> = [
    Step 1: Sequential agent A
    Step 2: Parallel { agent B, agent C, agent D }
    Step 3: Sequential agent E
  ]
  ↓
executePlanStep() → Pop first step from queue
  ↓
  ├─→ Sequential step? → executeAgent() → next step
  └─→ Parallel step? → executeAgent() for all (concurrently) → next step
  ↓
Repeat until queue is empty
  ↓
summarize() → Generate final answer
  ↓
END
```

### Key Capabilities

**1. Simple, Predictable Execution**
- Plan defined once upfront
- No dynamic decision making during execution
- Easy to reason about and debug

**2. Queue-Based Step Management**
- Steps execute in FIFO order
- Each step removed from queue after completion
- State tracks remaining steps for observability

**3. Parallel Execution Groups**
- Group multiple agents to execute concurrently
- Use `PlanStep.parallel(step1, step2, ...)` to create parallel groups
- Agent calls execute concurrently, state updates apply sequentially (safe)

**4. Safety Limits**
- Configurable `maxSteps()` to prevent infinite execution (default: 50)
- Workflow fails if max steps exceeded
- Useful for catching bugs in plan creation

**5. Durable State Management**
- Full workflow state persists across pauses and failures
- Composition-based state model via `WithSequentialPlanState`
- Separates orchestration state (remaining steps, history) from domain state
- Resumable after any step

### Required Implementations

When extending `SequentialPlanWorkflow`, you must implement:

- **createPlan()** - Create execution plan with sequential/parallel steps
  - Return state with list of `PlanStep` objects
  - Use `PlanStep.of(agentId, instruction)` for sequential steps
  - Use `PlanStep.parallel(step1, step2, ...)` for parallel groups
- **executeAgent(String agentId, String instruction)** - Execute single agent and update state
- **summarize()** - Generate final answer from all agent responses
- **handleFailure(String reason)** - Handle workflow failure

### Configuration Options

Override this method to customize behavior:

- **maxSteps()** - Maximum steps to execute before failing (default: 50)

### What Problems Does It Solve?

**1. Predictable Multi-Agent Workflows**
- When execution order is known upfront
- When task can be decomposed into clear sequential steps
- When you want simple, debuggable orchestration

**2. Parallel Agent Execution**
- Multiple independent agents can work concurrently
- Reduces total execution time for independent operations

**3. Structured Execution with Safety Bounds**
- Prevent runaway execution with max steps limit
- Clear separation of planning and execution phases
- Easy to understand what the workflow will do

### Real-World Use Cases

**Content Generation Pipeline**
- Plan: Research topic → Generate outline → Write sections in parallel → Edit → Format
- Each step is predictable and independent
- Parallel section writing speeds up execution
- No need for adaptive behavior

**Data Processing Workflow**
- Plan: Validate input → Transform in parallel (multiple transformers) → Aggregate → Store
- Fixed pipeline with known steps
- Parallel transformers for different data types
- Simple error handling (fail if any step fails)

**Multi-Source Information Gathering**
- Plan: Parallel { web search, database query, API call } → Merge results → Format
- All sources queried simultaneously
- No need to evaluate progress or adapt strategy
- Predictable cost and execution time

**Report Generation**
- Plan: Gather data → Parallel { generate charts, tables, summaries } → Compile report
- Well-defined steps with no branching logic
- Parallel generation of independent report sections
- Straightforward execution flow

---

## When to Use Which Pattern?

### Use AdaptiveLoopWorkflow When:

✅ **Execution path depends on agent results**
- Next agent to run depends on previous responses
- Need to make decisions during execution

✅ **Need to detect and recover from problems**
- Agents might get stuck or produce poor results
- Want automatic replanning when stalled

✅ **Complex multi-agent coordination required**
- Multiple rounds of interaction
- Uncertain how many agents or turns needed

✅ **Need human oversight for critical operations**
- Budget approvals, risk validation
- Human-in-the-loop at decision points

✅ **Task is exploratory or unpredictable**
- Research, investigation, complex problem-solving
- Can't define complete plan upfront

**Example Tasks:**
- "Research recent advances in quantum computing and write a detailed report"
- "Debug this production issue by investigating logs, code, and metrics"
- "Plan a trip with multiple constraints and preferences"

### Use SequentialPlanWorkflow When:

✅ **Execution order is defined upfront**
- Can define complete plan before execution starts
- The plan can still be created by an LLM
- No need for dynamic decision making

✅ **Simple linear or parallel execution sufficient**
- Clear sequential steps or independent parallel operations
- No branching logic required

✅ **No need for adaptive behavior**
- Don't need to evaluate progress or detect stalls
- Don't need replanning

✅ **Predictable, repeatable workflows**
- Same steps every time
- Easy to test and debug

**Example Tasks:**
- "Fetch data from these 5 APIs in parallel, then merge and format"
- "Generate report: gather data → create charts → write summary → compile"
- "Process document: validate → extract text → translate → summarize"

---

## Comparison Example: Trip Planning

### Same Task, Different Approaches

**Task**: Plan a 3-day trip to Paris

### AdaptiveLoopWorkflow Approach

```
1. gatherFacts: What's the budget? Interests? Constraints?
2. createPlan: Start with broad research
3. Inner Loop:
   Turn 1: Web search for Paris attractions
   - evaluateProgress: Found good options → continue
   Turn 2: Restaurant recommendations
   - evaluateProgress: Budget concerns → awaiting approval for expensive restaurant
   [Human approves higher budget]
   Turn 3: Book restaurant
   - evaluateProgress: Weather forecast shows rain → stalled (need to adjust plan)
   [Replan triggered]
4. updateFacts: Weather is rainy
5. updatePlan: Focus on indoor activities
6. Inner Loop continues with adjusted plan...
7. summarize: Complete itinerary with bookings
```

**Benefits**: Adapts to budget constraints, weather, availability. Human oversight for spending. Recovers from unexpected issues.

### SequentialPlanWorkflow Approach

```
1. createPlan: Define fixed steps
   - Step 1: Get weather forecast
   - Step 2: Parallel { search attractions, search restaurants, search hotels }
   - Step 3: Select best options (simple rules)
   - Step 4: Format itinerary
2. Execute each step in order
3. summarize: Output itinerary
```

**Benefits**: Fast, predictable, simple. Good when requirements are clear and unchanging.

---

## Implementation Patterns

### AdaptiveLoopWorkflow Implementation Pattern

```java
@Component(id = "adaptive-workflow")
public class MyAdaptiveWorkflow extends AdaptiveLoopWorkflow<MyState, MyAdaptiveWorkflow> {

  @Override
  protected MyState gatherFacts() {
    // Collect initial context
    String facts = callAgent("research-agent", "Gather facts about: " + task);
    return currentState().withFacts(facts);
  }

  @Override
  protected MyState createPlan() {
    // Create initial execution plan
    String plan = callAgent("planner-agent", "Create plan based on: " + facts);
    return currentState().withPlan(plan);
  }

  @Override
  protected ProgressEvaluation evaluateProgress(int turn) {
    // Evaluate after each agent response
    var evaluation = callAgent("orchestrator", "Evaluate progress for turn " + turn);

    if (evaluation.isComplete()) {
      return ProgressEvaluation.complete(evaluation.reason());
    }

    // HITL: Check if approval needed
    if (evaluation.requiresApproval()) {
      return ProgressEvaluation.awaitingApproval(
        evaluation.nextAgent(),
        evaluation.instruction(),
        evaluation.approvalContext()
      );
    }

    if (evaluation.isStalled()) {
      return ProgressEvaluation.stalled(
        evaluation.nextAgent(),
        evaluation.instruction(),
        evaluation.reason()
      );
    }

    return ProgressEvaluation.continueWith(evaluation.nextAgent(), evaluation.instruction());
  }

  @Override
  protected AgentExecutionEffect<?, MyState> executeAgent(String agentId, String instruction) {
    return AgentExecutionEffect
      .call(() -> callAgent(agentId, instruction))
      .updateState((response, state) -> state.addResponse(agentId, response));
  }

  @Override
  protected MyState summarize() {
    String answer = callAgent("summarizer", "Summarize all responses");
    return currentState().complete(answer);
  }

  // HITL handler
  public Effect<Done> approve(ApprovalDecision decision) {
    return handleApproval(decision.approvalId(), decision.approved());
  }
}
```

### SequentialPlanWorkflow Implementation Pattern

```java
@Component(id = "sequential-workflow")
public class MySequentialWorkflow extends SequentialPlanWorkflow<MyState, MySequentialWorkflow> {

  @Override
  protected MyState createPlan() {
    // Define complete plan upfront
    var steps = List.of(
      PlanStep.of("validator", "Validate input: " + task),
      PlanStep.parallel(
        PlanStep.of("source-a", "Fetch from source A"),
        PlanStep.of("source-b", "Fetch from source B"),
        PlanStep.of("source-c", "Fetch from source C")
      ),
      PlanStep.of("merger", "Merge all results"),
      PlanStep.of("formatter", "Format final output")
    );

    return currentState().withSteps(steps);
  }

  @Override
  protected AgentExecutionEffect<?, MyState> executeAgent(String agentId, String instruction) {
    return AgentExecutionEffect
      .call(() -> callAgent(agentId, instruction))
      .updateState((response, state) -> state.addResponse(agentId, response));
  }

  @Override
  protected MyState summarize() {
    // All steps complete, just return final formatted output
    return currentState().complete(currentState().getLastResponse());
  }
}
```

---

## Summary

**AdaptiveLoopWorkflow** is a powerful, self-correcting orchestrator for complex, unpredictable multi-agent workflows that need to adapt to real-world conditions. It provides intelligent stall detection, automatic replanning, HITL support, and comprehensive observability.

**SequentialPlanWorkflow** is a simple, efficient orchestrator for well-defined multi-agent workflows with predictable execution paths. It provides parallel execution capabilities with minimal complexity.

Choose the pattern that matches your task's complexity and predictability requirements. Start with SequentialPlanWorkflow for simple cases, and graduate to AdaptiveLoopWorkflow when you need adaptive behavior, error recovery, or human oversight.
