package zone.cogni.actionlogger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.inject.Inject;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = ControllerWithLogger.class)
@Import(LoggedActionAspectWebTwoInterceptorTest.TestConfiguration.class)
public class LoggedActionAspectWebTwoInterceptorTest extends AbstractLoggedActionAspectTest {

  @Inject
  private MockMvc mockMvc;

  @Configuration
  @EnableActionLogger
  static class TestConfiguration {

    @Bean
    public ControllerWithLogger controllerWithLogger() {
      return new ControllerWithLogger();
    }

    @Bean
    public CreateReportInterceptor createReportInterceptor() {
      return (request, report) -> report.put("interceptor", "1");
    }

    @Bean
    public CreateReportInterceptor createReportInterceptor2() {
      return (request, report) -> report.put("interceptor2", "2");
    }
  }

  @Test
  public void simpleTest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/test"))
           .andExpect(MockMvcResultMatchers.status().isOk());

    Assertions.assertThat(logReports)
              .hasSize(1);

    Assertions.assertThat(logReports.get(0))
              .containsEntry("interceptor", "1")
              .containsEntry("interceptor2", "2");
  }
}
