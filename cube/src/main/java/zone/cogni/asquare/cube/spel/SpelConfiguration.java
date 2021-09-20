package zone.cogni.asquare.cube.spel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpelConfiguration {

  @Bean(name = "spelService")
  public SpelService getSpelService() {
    return new SpelService();
  }

}
