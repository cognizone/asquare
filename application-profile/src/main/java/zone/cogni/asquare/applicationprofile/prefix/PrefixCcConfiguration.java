package zone.cogni.asquare.applicationprofile.prefix;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class PrefixCcConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "asquare.prefix-cc")
  public HashMap<String, String> getExtraCcPrefixes() {
    return new HashMap<>();
  }

}
