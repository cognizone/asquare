package zone.cogni.asquare.service.index;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import zone.cogni.asquare.service.async.AsyncTaskManager;

@Configuration
@EnableAsync(mode = AdviceMode.PROXY)
@EnableAspectJAutoProxy
@Import({GraphIndexService.class, IndexService.class, TypeIndexService.class})
public class IndexConfig {

  @Value("${index.corePoolSize:5}")
  private Integer corePoolSize;

  @Value("${index.maxPoolSize:10}")
  private Integer maxPoolSize;

  @Value("${index.queueCapacity:2}")
  private Integer queueCapacity;

  @Bean("indexingTaskExecutor")
  public AsyncTaskManager getIndexingTaskExecutor() {
    return new AsyncTaskManager("indexing task executor");
  }

  @Bean("indexingGraphTaskExecutor")
  public AsyncTaskManager getIndexingGraphTaskExecutor() {
    AsyncTaskManager asyncTaskManager = new AsyncTaskManager("indexing graph task executor", asyncContext -> asyncContext.get("graph"));
    asyncTaskManager.setCorePoolSize(corePoolSize);
    asyncTaskManager.setMaxPoolSize(maxPoolSize);
    //asyncTaskManager.setQueueCapacity(queueCapacity); // current code is using waiting sync mechanism, so no queue is needed, but it may be useful to queue tasks. Need to test that
    return asyncTaskManager;
  }

}
