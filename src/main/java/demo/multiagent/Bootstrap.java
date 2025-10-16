package demo.multiagent;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import com.typesafe.config.Config;
import demo.multiagent.application.DateTools;
import demo.multiagent.application.FakeWeatherService;
import demo.multiagent.application.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Setup
public class Bootstrap implements ServiceSetup {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public Bootstrap(Config config) {
    if (
      config.getString("akka.javasdk.agent.model-provider").equals("openai") &&
      config.getString("akka.javasdk.agent.openai.api-key").isBlank()
    ) {
      throw new RuntimeException(
        "No API keys found. Make sure you have OPENAI_API_KEY defined as environment variable."
      );
    }
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == WeatherService.class) {
          return (T) new FakeWeatherService();
        } else if (clazz == DateTools.class) {
          return (T) new DateTools();
        }
        return null;
      }
    };
  }
}
