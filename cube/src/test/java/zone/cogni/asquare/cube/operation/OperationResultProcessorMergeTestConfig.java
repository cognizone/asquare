package zone.cogni.asquare.cube.operation;

import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import zone.cogni.asquare.cube.spel.SpelService;

import java.util.Arrays;
import java.util.List;

@Configuration
@Import(SpelService.class)
public class OperationResultProcessorMergeTestConfig {

  @Autowired
  private SpelService spelService;

  @Bean
  OperationResultJsonConversion mergedRootConversion() {
    return new OperationResultJsonConversion(operationConfiguration(), mergedRootOperations());
  }

  @Bean
  OperationResultProcessor mergedRootOperations() {
    OperationConfiguration configuration = operationConfiguration();

    List<InputStreamSource> resources = Arrays.asList(new ClassPathResource("operation/operation-result-processor/operations-1.json5"),
                                                      new ClassPathResource("operation/operation-result-processor/operations-2.json5"));
    return new OperationResultProcessor(configuration,
                                        spelService,
                                        OperationRoot.getSupplier(resources));
  }

  @Bean
  public OperationConfiguration operationConfiguration() {
    OperationConfiguration configuration = new OperationConfiguration();
    configuration.setSecurityEnabled(false);
    configuration.setOutput(Lists.newArrayList("description", "selectorQuery", "operation", "requires", "template"));
    return configuration;
  }

}
