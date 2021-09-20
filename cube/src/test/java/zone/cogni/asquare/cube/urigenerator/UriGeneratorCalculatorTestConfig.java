package zone.cogni.asquare.cube.urigenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.SpelService;

@Configuration
@Import(SpelService.class)
public class UriGeneratorCalculatorTestConfig {

  @Autowired
  private SpelService spelService;

  @Bean
  public UriGeneratorCalculator uriCalculator() {
    Resource uriGeneratorsResource = new ClassPathResource("urigenerator/uri-generators.json5");
    return new UriGeneratorCalculator("http://resource",
                                      spelService,
                                      uriGeneratorsResource);
  }
}

