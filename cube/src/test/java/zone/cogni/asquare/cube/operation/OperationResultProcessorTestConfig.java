package zone.cogni.asquare.cube.operation;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.SpelService;

@Configuration
@Import(SpelService.class)
public class OperationResultProcessorTestConfig {

  @Autowired
  private SpelService spelService;

  @Bean
  OperationResultProcessor personOperations() {
    OperationConfiguration configuration = personOperationConfiguration();

    Resource personOperations = new ClassPathResource("operation/person.operations.json5");

    return new OperationResultProcessor(configuration, spelService, personOperations);
  }

  @Bean
  public OperationConfiguration personOperationConfiguration() {
    OperationConfiguration configuration = new OperationConfiguration();
    configuration.setSecurityEnabled(false);
    configuration.setOutput(Lists.newArrayList("description", "selectorQuery", "operation", "requires", "template"));
    return configuration;
  }

  @Bean
  OperationResultJsonConversion personConversion() {
    return new OperationResultJsonConversion(personOperationConfiguration(), personOperations());
  }
}
