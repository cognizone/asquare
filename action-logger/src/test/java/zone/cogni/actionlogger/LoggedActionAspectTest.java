package zone.cogni.actionlogger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
                classes = LoggedActionAspectTest.TestConfiguration.class)
public class LoggedActionAspectTest extends AbstractLoggedActionAspectTest {

  @Inject
  private ServiceWithLoggers serviceWithLoggers;
  @Inject
  private ServiceWithLoggers2 serviceWithLoggers2;

  @Configuration
  @EnableActionLogger
  static class TestConfiguration {
    @Bean
    public ServiceWithLoggers serviceWithLoggers() {
      return new ServiceWithLoggers();
    }

    @Bean
    public ServiceWithLoggers2 serviceWithLoggers2() {
      return new ServiceWithLoggers2();
    }
  }

  @Test
  public void simpleTest() {
    serviceWithLoggers.simple();
    Assertions.assertThat(logReports)
              .hasSize(1);
  }

  @Test
  public void requestorAndNameTest() {
    serviceWithLoggers.simple("C'est moi");
    serviceWithLoggers.simple("C'est moi aussi");

    Assertions.assertThat(logReports)
            .hasSize(2);

    Assertions.assertThat(logReports.get(0))
            .containsEntry(LoggedActionModel.ReportKeys.success, true)
            .containsEntry(LoggedActionModel.ReportKeys.requestorName, "C'est moi")
            .containsEntry(LoggedActionModel.ReportKeys.name, "simple")
            .doesNotContainKey(LoggedActionModel.ReportKeys.parentId);

    Assertions.assertThat(logReports.get(1))
            .containsEntry(LoggedActionModel.ReportKeys.success, true)
            .containsEntry(LoggedActionModel.ReportKeys.requestorName, "C'est moi aussi")
            .containsEntry(LoggedActionModel.ReportKeys.name, "simple")
            .doesNotContainKey(LoggedActionModel.ReportKeys.parentId);

  }

  @Test
  public void withParent() {
    serviceWithLoggers2.withParent();

    Assertions.assertThat(logReports)
            .hasSize(2);

    Assertions.assertThat(logReports.get(0))
            .containsEntry(LoggedActionModel.ReportKeys.success, true)
            .containsEntry(LoggedActionModel.ReportKeys.parentId, logReports.get(1).get(LoggedActionModel.ReportKeys.id));

    Assertions.assertThat(logReports.get(1))
            .containsEntry(LoggedActionModel.ReportKeys.success, true)
            .doesNotContainKey(LoggedActionModel.ReportKeys.parentId);


    String parentStart = (String) logReports.get(1).get(LoggedActionModel.ReportKeys.start);
    String childStart = (String) logReports.get(0).get(LoggedActionModel.ReportKeys.start);
    String childEnd = (String) logReports.get(0).get(LoggedActionModel.ReportKeys.end);
    String parentEnd = (String) logReports.get(1).get(LoggedActionModel.ReportKeys.end);

    Assertions.assertThat(parentStart)
            .isLessThan(childStart)
            .isLessThan(childEnd)
            .isLessThan(parentEnd);
    Assertions.assertThat(childStart)
            .isLessThan(childEnd)
            .isLessThan(parentEnd);
    Assertions.assertThat(childEnd)
            .isLessThan(parentEnd);
  }

  @Test
  public void addInfo() {
    LocalDateTime now = LocalDateTime.now();
    serviceWithLoggers.addInfo("someKey", now);
    Assertions.assertThat(logReports)
            .hasSize(1);

    Assertions.assertThat(LoggedActionModel.getActionInfoThreadLocal().get())
            .isNull();

    Assertions.assertThat((Map<String, Object>) logReports.get(0).get(LoggedActionModel.ReportKeys.actionInfo))
            .containsEntry("someKey", now);

  }
}