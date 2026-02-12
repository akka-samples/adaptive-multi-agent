package demo.swarm_class2;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.swarm_class.Handoff;
import akka.javasdk.swarm_class.SwarmParams;
import akka.javasdk.swarm_class.SwarmResult;
import akka.javasdk.swarm_class2.client.ComponentClient;
import demo.swarm7.EditorAgent;
import demo.swarm7.EvaluatorAgent;
import demo.swarm7.ResearcherAgent;
import demo.swarm7.WriterAgent;

import java.util.UUID;

/**
 * HTTP endpoint demonstrating how to interact with class-based swarms (swarm_class2 variant).
 *
 * <p>Each top-level swarm has start and result endpoints. Swarms that support HITL
 * (pause/resume) also have resume endpoints. Child swarms like {@link TicketingSwarm}
 * are not exposed — they are invoked internally by their parent swarm.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/swarm-class2")
public class SwarmEndpoint {

  public record StartRequest(String message) {}
  public record ResumeRequest(String message) {}
  public record StopRequest(String reason) {}
  public record StartedResponse(String swarmId) {}

  private final ComponentClient componentClient;

  public SwarmEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // ==================== Triage (swarm1) ====================

  @Post("/triage")
  public HttpResponse startTriage(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(TriageSwarm.class, swarmId).run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  @Get("/triage/{swarmId}")
  public HttpResponse getTriageResult(String swarmId) {
    return toResponse(componentClient.forSwarm(TriageSwarm.class, swarmId).getResult());
  }

  // ==================== Activity Planner (swarm2) ====================

  @Post("/activity-planner")
  public HttpResponse startActivityPlanner(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  @Get("/activity-planner/{swarmId}")
  public HttpResponse getActivityPlannerResult(String swarmId) {
    return toResponse(componentClient.forSwarm(ActivityPlannerSwarm.class, swarmId).getResult());
  }

  // ==================== Records Search (swarm3) ====================

  @Post("/records-search")
  public HttpResponse startRecordsSearch(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(RecordsSearchSwarm.class, swarmId).run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  @Get("/records-search/{swarmId}")
  public HttpResponse getRecordsSearchResult(String swarmId) {
    return toResponse(componentClient.forSwarm(RecordsSearchSwarm.class, swarmId).getResult());
  }

  // ==================== Policy Re-Rating (swarm4) — HITL ====================

  @Post("/policy-re-rating")
  public HttpResponse startPolicyReRating(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(PolicyReRatingSwarm.class, swarmId).run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  @Get("/policy-re-rating/{swarmId}")
  public HttpResponse getPolicyReRatingResult(String swarmId) {
    return toResponse(componentClient.forSwarm(PolicyReRatingSwarm.class, swarmId).getResult());
  }

  @Post("/policy-re-rating/{swarmId}/resume")
  public HttpResponse resumePolicyReRating(String swarmId, ResumeRequest request) {
    componentClient.forSwarm(PolicyReRatingSwarm.class, swarmId).resume(request.message());
    return HttpResponses.ok();
  }

  // ==================== Activity Booking (swarm5) — composition + HITL ====================
  // Internally composes ActivityPlannerSwarm and TicketingSwarm — those are not exposed here.

  @Post("/activity-booking")
  public HttpResponse startActivityBooking(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(ActivityBookingSwarm.class, swarmId).run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  @Get("/activity-booking/{swarmId}")
  public HttpResponse getActivityBookingResult(String swarmId) {
    return toResponse(componentClient.forSwarm(ActivityBookingSwarm.class, swarmId).getResult());
  }

  @Post("/activity-booking/{swarmId}/resume")
  public HttpResponse resumeActivityBooking(String swarmId, ResumeRequest request) {
    componentClient.forSwarm(ActivityBookingSwarm.class, swarmId).resume(request.message());
    return HttpResponses.ok();
  }

  // ==================== Content Refinement (swarm6) ====================

  @Post("/content-refinement")
  public HttpResponse startContentRefinement(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(ContentRefinementSwarm.class, swarmId).run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  @Get("/content-refinement/{swarmId}")
  public HttpResponse getContentRefinementResult(String swarmId) {
    return toResponse(componentClient.forSwarm(ContentRefinementSwarm.class, swarmId).getResult());
  }

  // ==================== Content Creation (swarm7) — HITL ====================

  @Post("/content-creation")
  public HttpResponse startContentCreation(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(ContentCreationSwarm.class, swarmId).run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  /**
   * Start content creation with call-site parameter overrides.
   *
   * <p>Demonstrates {@code withParameters} — here we add a fact-checker agent to the
   * standard content creation pipeline and increase maxTurns to accommodate the extra phase.
   * The swarm class still determines the result type ({@code ContentResult}).
   */
  @Post("/content-creation-with-fact-check")
  public HttpResponse startContentCreationWithFactCheck(StartRequest request) {
    var swarmId = UUID.randomUUID().toString();
    componentClient.forSwarm(ContentCreationSwarm.class, swarmId)
        .withParameters(SwarmParams.builder()
            .instructions("""
                You are a content production orchestrator with fact-checking.

                Follow this process:
                1. Research the topic using the researcher agent
                2. Write the content using the writer agent
                3. Edit using the editor agent
                4. Fact-check all claims using the fact-checker agent
                5. If the fact-checker finds inaccuracies, send back to the writer
                   with corrections, then re-edit and re-check
                6. Once fact-checked and polished, evaluate with the evaluator agent
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
        .run(request.message());
    return HttpResponses.created(new StartedResponse(swarmId));
  }

  @Get("/content-creation/{swarmId}")
  public HttpResponse getContentCreationResult(String swarmId) {
    return toResponse(componentClient.forSwarm(ContentCreationSwarm.class, swarmId).getResult());
  }

  @Post("/content-creation/{swarmId}/resume")
  public HttpResponse resumeContentCreation(String swarmId, ResumeRequest request) {
    componentClient.forSwarm(ContentCreationSwarm.class, swarmId).resume(request.message());
    return HttpResponses.ok();
  }

  // ==================== Generic stop ====================

  @Post("/triage/{swarmId}/stop")
  public HttpResponse stopTriage(String swarmId, StopRequest request) {
    componentClient.forSwarm(TriageSwarm.class, swarmId).stop(request.reason());
    return HttpResponses.ok();
  }

  @Post("/activity-booking/{swarmId}/stop")
  public HttpResponse stopActivityBooking(String swarmId, StopRequest request) {
    componentClient.forSwarm(ActivityBookingSwarm.class, swarmId).stop(request.reason());
    return HttpResponses.ok();
  }

  @Post("/content-creation/{swarmId}/stop")
  public HttpResponse stopContentCreation(String swarmId, StopRequest request) {
    componentClient.forSwarm(ContentCreationSwarm.class, swarmId).stop(request.reason());
    return HttpResponses.ok();
  }

  // ==================== Helpers ====================

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
