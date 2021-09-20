package zone.cogni.asquare.applicationprofile.prefix;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@EnableAutoConfiguration
@EnableConfigurationProperties
@Import({PrefixCcService.class})
@TestPropertySource(locations = "classpath:prefix/application-test.properties")
public class PrefixCcConfigurationTest {

  @Autowired
  PrefixCcConfiguration prefixCcConfiguration;

  @Autowired
  PrefixCcService prefixCcService;

  @Test
  void check_prefix_in_properties_file() {
    HashMap<String, String> extraCcPrefixes = prefixCcConfiguration.getExtraCcPrefixes();

    assertThat(extraCcPrefixes.size()).isEqualTo(3);
    assertThat(extraCcPrefixes.get("one")).isEqualTo("een");
    assertThat(extraCcPrefixes.get("two")).isEqualTo("twee");
    assertThat(extraCcPrefixes.get("cz-onto")).isEqualTo("http://example.cogni.zone/example-ontology/");
  }

  @Test
  void check_prefix_cc_in_application_yml() {
    assertThat(prefixCcService.getNamespace("cz-onto")).isEqualTo("http://example.cogni.zone/example-ontology/");
  }

}
