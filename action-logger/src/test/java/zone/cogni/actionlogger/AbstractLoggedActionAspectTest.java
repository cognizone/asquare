package zone.cogni.actionlogger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Import(AbstractLoggedActionAspectTest.TestConfiguration.class)
public abstract class AbstractLoggedActionAspectTest {

  protected static final List<Map<String, Object>> logReports = new ArrayList<>();

  @Configuration
  @EnableActionLogger
  public static class TestConfiguration {
    @Bean
    public LoggedActionSaver loggedActionSaver() {
      return logReports::add;
    }
  }

  @BeforeEach
  public void beforeTest_AbstractLoggedActionAspectTest() {
    logReports.clear();
  }

}
