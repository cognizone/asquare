package zone.cogni.asquare.cube.sparql2json;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.SpelService;

import java.util.Collections;

@Configuration
@Import(SpelService.class)
public class SparqlSelectToJsonTestConfig {
  @Value("classpath:sparql2json/facets/*")
  private Resource[] facetQueries;

  @Autowired
  private SpelService spelService;

  @Bean
  public SparqlSelectToJson selectToJson(){
    return new SparqlSelectToJson(facetQueries, spelService, Collections.emptyMap());
  }
}
