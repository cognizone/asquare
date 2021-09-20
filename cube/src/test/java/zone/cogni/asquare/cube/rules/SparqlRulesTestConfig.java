package zone.cogni.asquare.cube.rules;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import zone.cogni.asquare.cube.spel.SpelService;

@Configuration
@Import(SpelService.class)
public class SparqlRulesTestConfig {
  @Autowired
  private SpelService spelService;
  @Value("classpath:rules")
  private String rulesFolder;

  @Bean
  SparqlRules personRules() {
    return new SparqlRules(spelService, rulesFolder);
  }
}
