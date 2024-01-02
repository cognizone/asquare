package zone.cogni.actionlogger;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class LoggedActionConfiguration {

  private final LoggedActionSaver loggedActionSaver;
  @Autowired(required = false)
  private final List<CreateReportInterceptor> createReportInterceptors;
  @Value("${cognizone.actionlogger.async.enabled:false}")
  private final boolean async;
  @Value("${cognizone.actionlogger.async.queueCapacity:1000}")
  private final int queueCapacity;


  @Bean
  public LoggedActionAspect loggedActionAspect() {
    return new LoggedActionAspect(loggedActionSaver,
                                  null == createReportInterceptors ? Collections.emptyList() : createReportInterceptors,
                                  loggedActionTaskExecutor());
  }

  @Bean
  public TaskExecutor loggedActionTaskExecutor() {
    if (!async) return new SyncTaskExecutor();

    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    taskExecutor.setCorePoolSize(1);
    taskExecutor.setMaxPoolSize(1);
    taskExecutor.setQueueCapacity(queueCapacity);
    taskExecutor.setThreadNamePrefix("LoggedActionThread");
    return taskExecutor;
  }

}
