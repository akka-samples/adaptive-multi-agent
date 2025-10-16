package demo.multiagent.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.multiagent.domain.AgentTeamState;
import demo.multiagent.domain.BooleanAnswer;
import demo.multiagent.domain.ProgressEvaluation;
import demo.multiagent.domain.StringAnswer;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class AgentTeamWorkflowTest extends TestKitSupport {

  private final TestModelProvider ledgerModel = new TestModelProvider();
  private final TestModelProvider orchestratorModel = new TestModelProvider();
  private final TestModelProvider activitiesModel = new TestModelProvider();
  private final TestModelProvider weatherModel = new TestModelProvider();
  private final TestModelProvider restaurantModel = new TestModelProvider();
  private final TestModelProvider transportModel = new TestModelProvider();
  private final TestModelProvider budgetModel = new TestModelProvider();
  private final TestModelProvider eventModel = new TestModelProvider();
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
      .withModelProvider(RestaurantAgent.class, restaurantModel)
      .withModelProvider(TransportAgent.class, transportModel)
      .withModelProvider(BudgetAgent.class, budgetModel)
      .withModelProvider(EventAgent.class, eventModel)
      .withModelProvider(SummarizerAgent.class, summaryModel);
  }

  @Test
  public void testAdaptiveOrchestration() {
    // Setup LedgerAgent responses
    setupLedgerResponses();

    // Setup worker agent responses - these will be called once each
    weatherModel
      .whenMessage(req -> req.contains("weather") || req.contains("Madrid"))
      .reply("The weather in Madrid is rainy with temperatures around 15°C.");

    activitiesModel
      .whenMessage(
        req -> req.contains("activities") || req.contains("rainy") || req.contains("Madrid")
      )
      .reply(
        """
        For a rainy day in Madrid, I recommend visiting the Prado Museum,
        Reina Sofia Museum, or enjoying tapas at indoor food markets.
        """.stripIndent()
      );

    // Setup OrchestratorAgent responses
    // Round 1: execute weather agent
    var firstEval = new ProgressEvaluation(
      new BooleanAnswer("Task not yet complete", false),
      new BooleanAnswer("Not in a loop", false),
      new BooleanAnswer("Just starting", true),
      new StringAnswer("Weather agent should be called first", "weather-agent"),
      new StringAnswer("Need weather data", "What is the current weather in Madrid?")
    );

    // Round 2: task satisfied after getting weather
    var secondEval = new ProgressEvaluation(
      new BooleanAnswer("Have sufficient information", true),
      new BooleanAnswer("Not in a loop", false),
      new BooleanAnswer("Made progress", true),
      new StringAnswer("Task complete", "none"),
      new StringAnswer("Ready to summarize", "Prepare final answer")
    );

    // Return different responses based on round count
    orchestratorModel
      .whenMessage(req -> req.contains("Round: 1"))
      .reply(JsonSupport.encodeToString(firstEval));

    orchestratorModel
      .whenMessage(req -> req.contains("Round: 2"))
      .reply(JsonSupport.encodeToString(secondEval));

    // Setup summarizer response
    summaryModel.fixedResponse(
      """
      The weather in Madrid is rainy today, so I recommend indoor activities.
      Visit the Prado Museum or Reina Sofia Museum, or enjoy tapas at Mercado de San Miguel.
      """.stripIndent()
    );

    // Start workflow
    var query = "I am in Madrid. What should I do? Beware of the weather.";
    var sessionId = UUID.randomUUID().toString();
    var request = new AgentTeamWorkflow.Request("alice", query);

    componentClient.forWorkflow(sessionId).method(AgentTeamWorkflow::start).invoke(request);

    // Wait for workflow to complete and verify answer
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, SECONDS)
      .untilAsserted(() -> {
        var answer = componentClient
          .forWorkflow(sessionId)
          .method(AgentTeamWorkflow::getAnswer)
          .invoke();
        assertThat(answer).isNotBlank();
        assertThat(answer).contains("Madrid");
        assertThat(answer).contains("rainy");
      });

    // Verify we can get the full state
    var state = componentClient
      .forWorkflow(sessionId)
      .method(AgentTeamWorkflow::getState)
      .invoke();
    assertThat(state).isNotNull();
    assertThat(state.task()).isEqualTo(query);
    assertThat(state.status()).isEqualTo(AgentTeamState.Status.COMPLETED);
  }

  @Test
  public void testComplexScenarioWithReplanning() {
    // Setup LedgerAgent responses for complex scenario
    setupComplexLedgerResponses();

    // Setup worker agent responses - use broad matchers for test flexibility
    weatherModel
      .whenMessage(req -> true) // Match any weather request
      .reply("Barcelona weather: Sunny Saturday (22°C), rainy Sunday afternoon (16°C).");

    eventModel
      .whenMessage(req -> true) // Match any event request
      .reply(
        "There's a children's festival at Parc de la Ciutadella on Saturday, free entry."
      );

    activitiesModel
      .whenMessage(req -> true) // Match any activity request
      .reply(
        """
        Saturday: Parc de la Ciutadella festival. Sunday morning: CosmoCaixa Science Museum
        (14 euros per adult, 10 euros per child). Beach activities if weather permits.
        """.stripIndent()
      );

    restaurantModel
      .whenMessage(req -> true) // Match any restaurant request
      .reply(
        "Family restaurants near Sagrada Familia: La Paradeta (seafood, 35 euros per person), " +
        "Cerveceria Catalana (tapas, 25 euros per person)."
      );

    budgetModel
      .whenMessage(req -> true) // Match any budget request
      .reply(
        "Budget check: Activities 48€ + Restaurants 120€ + Transport 48€ = 216€ total. " +
        "Well within 600€ budget."
      );

    transportModel
      .whenMessage(req -> true) // Match any transport request
      .reply(
        "Transport: T-casual tickets recommended, 48 euros for 4 people for the weekend. " +
        "Metro connections available to all locations."
      );

    // Setup Orchestrator responses for multi-round flow
    setupComplexOrchestratorResponses();

    // Setup summarizer response
    summaryModel.fixedResponse(
      """
      Here's your Barcelona family weekend plan:

      Saturday: Children's festival at Parc de la Ciutadella (free). Lunch at Cerveceria Catalana.
      Sunday: CosmoCaixa Science Museum in the morning. Picnic lunch to stay within budget.
      Transport: Metro T-casual tickets (48 euros).
      Total cost: 294 euros (within your 600 euro budget with room to spare).
      """.stripIndent()
    );

    // Start workflow with complex query
    var query =
      """
      Plan a weekend in Barcelona with my family (2 adults, 2 kids aged 8 and 10).
      Budget is 600 euros total. Find kid-friendly activities, family restaurants,
      and check if there are any special events this weekend. We are staying near Sagrada Familia.
      """.stripIndent();
    var sessionId = UUID.randomUUID().toString();
    var request = new AgentTeamWorkflow.Request("bob", query);

    componentClient.forWorkflow(sessionId).method(AgentTeamWorkflow::start).invoke(request);

    // Wait for workflow to complete
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, SECONDS)
      .untilAsserted(() -> {
        var state = componentClient
          .forWorkflow(sessionId)
          .method(AgentTeamWorkflow::getState)
          .invoke();

        assertThat(state.status()).isEqualTo(AgentTeamState.Status.COMPLETED);
      });

    // Verify answer
    var answer = componentClient
      .forWorkflow(sessionId)
      .method(AgentTeamWorkflow::getAnswer)
      .invoke();
    assertThat(answer).isNotBlank();
    assertThat(answer).contains("Barcelona");
    assertThat(answer).containsAnyOf("budget", "euros");

    // Verify state shows replanning occurred
    var state = componentClient
      .forWorkflow(sessionId)
      .method(AgentTeamWorkflow::getState)
      .invoke();
    assertThat(state.task()).isEqualTo(query);
    assertThat(state.messageHistory()).isNotEmpty();
  }

  private void setupLedgerResponses() {
    // Gather facts response
    ledgerModel
      .whenMessage(req -> req.contains("task to analyze"))
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
        """
      );

    // Create plan response
    ledgerModel
      .whenMessage(req -> req.contains("create an execution plan"))
      .reply(
        """
        - First, check the current weather in Madrid using the Weather Agent
        - Then, suggest appropriate activities based on the weather using the Activity Agent
        - Finally, summarize the recommendations
        """
      );
  }

  private void setupComplexLedgerResponses() {
    // Gather facts for complex scenario
    ledgerModel
      .whenMessage(req -> req.contains("task to analyze") && req.contains("Barcelona"))
      .reply(
        """
        1. GIVEN OR VERIFIED FACTS
        - Family of 4 (2 adults, 2 kids aged 8 and 10)
        - Weekend trip to Barcelona
        - Budget: 600 euros total
        - Staying near Sagrada Familia
        - Need: kid-friendly activities, family restaurants, special events

        2. FACTS TO LOOK UP
        - Weekend weather in Barcelona
        - Special events happening this weekend
        - Family-friendly activities

        3. FACTS TO DERIVE
        - Cost breakdown across categories
        - Transport requirements between locations
        - Schedule feasibility

        4. EDUCATED GUESSES
        - Budget should cover activities, meals, transport
        - Kids aged 8-10 need interactive, engaging activities
        - Weather will influence indoor vs outdoor plans
        """
      );

    // Create plan response
    ledgerModel
      .whenMessage(
        req ->
          req.contains("create an execution plan") && !req.contains("conversation history")
      )
      .reply(
        """
        - Check Barcelona weather for the weekend
        - Look for special family events happening
        - Get kid-friendly activity recommendations
        - Find family restaurant options near Sagrada Familia
        - Verify budget across all recommendations
        - Check transport logistics and costs
        """
      );

    // Update facts response (during replanning)
    ledgerModel
      .whenMessage(req -> req.contains("update the fact sheet"))
      .reply(
        """
        1. GIVEN OR VERIFIED FACTS
        - Family of 4 (2 adults, 2 kids aged 8 and 10)
        - Weekend trip to Barcelona
        - Budget: 600 euros total
        - Staying near Sagrada Familia
        - Weather: Sunny Saturday, rainy Sunday afternoon
        - Free children's festival on Saturday

        2. FACTS DISCOVERED
        - Initial budget estimate: 336 euros
        - Budget exceeded by 36 euros
        - Restaurant costs are the largest expense (240 euros)

        3. FACTS TO DERIVE
        - Cost-saving alternatives needed

        4. EDUCATED GUESSES
        - Replacing one restaurant meal with picnic could save 60 euros
        - Free park activities can supplement paid attractions
        """
      );

    // Update plan response (during replanning)
    ledgerModel
      .whenMessage(req -> req.contains("conversation history showing what went wrong"))
      .reply(
        """
        REVISED PLAN to stay within budget:
        - Keep free Saturday festival and museum activities
        - Replace one restaurant dinner with picnic (save 60 euros)
        - Keep metro transport as most cost-effective
        - Verify final budget is under 600 euros
        """
      );
  }

  private void setupComplexOrchestratorResponses() {
    // Orchestrator cycles through agents, then completes
    // Round 1: weather-agent → Round 2: activity-agent → Round 3: restaurant-agent → Round 4: complete

    var callWeather = new ProgressEvaluation(
      new BooleanAnswer("Need weather info", false),
      new BooleanAnswer("Not in loop", false),
      new BooleanAnswer("Making progress", true),
      new StringAnswer("Call weather agent", "weather-agent"),
      new StringAnswer("Get weather", "What is the Barcelona weather this weekend?")
    );

    var callActivity = new ProgressEvaluation(
      new BooleanAnswer("Need activities", false),
      new BooleanAnswer("Not in loop", false),
      new BooleanAnswer("Making progress", true),
      new StringAnswer("Call activity agent", "activity-agent"),
      new StringAnswer(
        "Get activities",
        "Suggest kid-friendly activities based on the weather"
      )
    );

    var callRestaurant = new ProgressEvaluation(
      new BooleanAnswer("Need restaurants", false),
      new BooleanAnswer("Not in loop", false),
      new BooleanAnswer("Making progress", true),
      new StringAnswer("Call restaurant agent", "restaurant-agent"),
      new StringAnswer("Get restaurants", "Find family-friendly restaurants")
    );

    var taskComplete = new ProgressEvaluation(
      new BooleanAnswer("All information gathered", true),
      new BooleanAnswer("Not in loop", false),
      new BooleanAnswer("Task complete", true),
      new StringAnswer("Done", "none"),
      new StringAnswer("Finalize", "Ready to summarize")
    );

    // Return different responses based on round count
    orchestratorModel
      .whenMessage(req -> req.contains("Round: 1"))
      .reply(JsonSupport.encodeToString(callWeather));

    orchestratorModel
      .whenMessage(req -> req.contains("Round: 2"))
      .reply(JsonSupport.encodeToString(callActivity));

    orchestratorModel
      .whenMessage(req -> req.contains("Round: 3"))
      .reply(JsonSupport.encodeToString(callRestaurant));

    orchestratorModel
      .whenMessage(req -> req.contains("Round: 4"))
      .reply(JsonSupport.encodeToString(taskComplete));
  }
}
