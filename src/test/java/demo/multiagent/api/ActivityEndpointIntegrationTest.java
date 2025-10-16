package demo.multiagent.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.multiagent.application.ActivityAgent;
import demo.multiagent.application.LedgerAgent;
import demo.multiagent.application.OrchestratorAgent;
import demo.multiagent.application.SummarizerAgent;
import demo.multiagent.application.WeatherAgent;
import demo.multiagent.domain.BooleanAnswer;
import demo.multiagent.domain.ProgressEvaluation;
import demo.multiagent.domain.StringAnswer;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ActivityEndpointIntegrationTest extends TestKitSupport {

  private final TestModelProvider ledgerModel = new TestModelProvider();
  private final TestModelProvider orchestratorModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider summaryModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(LedgerAgent.class, ledgerModel)
      .withModelProvider(OrchestratorAgent.class, orchestratorModel)
      .withModelProvider(ActivityAgent.class, activitiesModel)
      .withModelProvider(WeatherAgent.class, weatherModel)
      .withModelProvider(SummarizerAgent.class, summaryModel);
  }

  @Test
  public void shouldHandleAdaptiveOrchestration() {
    var userId = "bob";
    var query = "I am in Madrid. What should I do? Beware of the weather.";

    // Setup model responses for outer loop (facts & plan)
    setupLedgerResponses();

    // Setup model responses for inner loop (progress evaluation & agent execution)
    setupProgressEvaluationResponses();
    setupWorkerAgentResponses();
    setupSummaryResponse();

    // 1. Start workflow
    var startResponse = httpClient
      .POST("/activities/" + userId)
      .withRequestBody(new ActivityEndpoint.Request(query))
      .invoke();

    assertThat(startResponse.status()).isEqualTo(StatusCodes.CREATED);

    // Extract sessionId from Location header
    var locationHeader = startResponse
      .httpResponse()
      .getHeader("Location")
      .orElseThrow()
      .value();
    var sessionId = extractSessionIdFromLocation(locationHeader, userId);

    // 2. Wait for workflow to complete and retrieve answer
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var answerResponse = httpClient
          .GET("/activities/" + userId + "/" + sessionId)
          .responseBodyAs(String.class)
          .invoke();
        assertThat(answerResponse.status()).isEqualTo(StatusCodes.OK);

        var answer = answerResponse.body();
        assertThat(answer).isNotBlank();
        // Verify the summary contains expected information from both agents
        assertThat(answer).contains("rainy");
        assertThat(answer).contains("Madrid");
        assertThat(answer).containsAnyOf("Museum", "museum", "Prado", "Mercado");
      });

    // 3. Verify workflow state can be retrieved
    var stateResponse = httpClient
      .GET("/activities/" + userId + "/" + sessionId + "/state")
      .invoke();
    assertThat(stateResponse.status()).isEqualTo(StatusCodes.OK);
  }

  private void setupLedgerResponses() {
    // Gather facts response - matches "Please provide the fact-gathering pre-survey as instructed."
    ledgerModel
      .whenMessage(req -> req.contains("fact-gathering pre-survey"))
      .reply(
        """
        1. GIVEN OR VERIFIED FACTS
        - User is in Madrid
        - User wants activity suggestions
        - Weather needs to be considered

        2. FACTS TO LOOK UP
        - Current weather in Madrid

        3. FACTS TO DERIVE
        - Suitable activities based on weather

        4. EDUCATED GUESSES
        - If weather is rainy, indoor activities like museums would be better
        - If weather is sunny, outdoor activities like parks would be suitable
        """
      );

    // Create plan response - matches "Please create an execution plan."
    ledgerModel
      .whenMessage(req -> req.contains("execution plan"))
      .reply(
        """
        - First, check the current weather in Madrid using the Weather Agent
        - Then, based on the weather, suggest appropriate activities using the Activity Agent
        - Finally, summarize the recommendations
        """
      );
  }

  private void setupProgressEvaluationResponses() {
    // First evaluation (Round 1): select WeatherAgent
    var firstEvaluation = new ProgressEvaluation(
      new BooleanAnswer("Need weather information", false),
      new BooleanAnswer("Not in a loop", false),
      new BooleanAnswer("Made progress", true),
      new StringAnswer("Get weather", "weather-agent"),
      new StringAnswer("Check weather first", "Get weather information for Madrid")
    );

    // Second evaluation (Round 2): select ActivityAgent
    var secondEvaluation = new ProgressEvaluation(
      new BooleanAnswer("Need activity suggestions", false),
      new BooleanAnswer("Not in a loop", false),
      new BooleanAnswer("Made progress", true),
      new StringAnswer("Get activities", "activity-agent"),
      new StringAnswer("Suggest activities", "Suggest activities based on weather")
    );

    // Third evaluation (Round 3): mark as satisfied to trigger summarization
    var thirdEvaluation = new ProgressEvaluation(
      new BooleanAnswer("Task satisfied - have weather and activities", true),
      new BooleanAnswer("Not in a loop", false),
      new BooleanAnswer("Made progress", true),
      new StringAnswer("Task complete", "none"),
      new StringAnswer("Ready to summarize", "Prepare final answer")
    );

    // Return different evaluations based on the round count
    orchestratorModel
      .whenMessage(req -> req.contains("Round: 1"))
      .reply(JsonSupport.encodeToString(firstEvaluation));

    orchestratorModel
      .whenMessage(req -> req.contains("Round: 2"))
      .reply(JsonSupport.encodeToString(secondEvaluation));

    orchestratorModel
      .whenMessage(req -> req.contains("Round: 3"))
      .reply(JsonSupport.encodeToString(thirdEvaluation));
  }

  private void setupWorkerAgentResponses() {
    // Weather agent response - match any request about weather/Madrid
    weatherModel
      .whenMessage(req -> req.contains("weather") || req.contains("Madrid"))
      .reply("The weather in Madrid today is rainy with temperatures around 15°C.");

    // Activity agent response - match any request about activities/rainy/Madrid
    activitiesModel
      .whenMessage(
        req -> req.contains("activities") || req.contains("rainy") || req.contains("Madrid")
      )
      .reply(
        """
        For a rainy day in Madrid, I recommend visiting the Prado Museum,
        Reina Sofia Museum, or enjoying tapas at indoor food markets like Mercado de San Miguel.
        """.stripIndent()
      );
  }

  private void setupSummaryResponse() {
    summaryModel.fixedResponse(
      """
      The weather in Madrid is rainy today, so I recommend indoor activities.
      You should visit the Prado Museum or Reina Sofia Museum, both world-class art galleries.
      Alternatively, enjoy traditional Spanish tapas at the indoor Mercado de San Miguel.
      """.stripIndent()
    );
  }

  private String extractSessionIdFromLocation(String locationHeader, String userId) {
    // Location header format: /activities/{userId}/{sessionId}
    var prefix = "/activities/" + userId + "/";
    if (locationHeader.startsWith(prefix)) {
      return locationHeader.substring(prefix.length());
    }
    throw new IllegalArgumentException("Invalid location header format: " + locationHeader);
  }
}
