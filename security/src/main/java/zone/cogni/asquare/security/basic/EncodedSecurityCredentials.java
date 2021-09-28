package zone.cogni.asquare.security.basic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
@ConfigurationProperties(prefix = "cognizone.security.basic")
public class EncodedSecurityCredentials {
  private HashMap<String, String> encodedCredentials;

  public HashMap<String, String> getEncodedCredentials() {
    return encodedCredentials;
  }

  public void setEncodedCredentials(HashMap<String, String> encodedCredentials) {
    this.encodedCredentials = encodedCredentials;
  }
}
