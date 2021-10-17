package zone.cogni.asquare.applicationprofile.model.basic;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileSimpleJson;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ApplicationProfileConfig.class)
class CollapseApplicationProfileTest {

  @Autowired
  private ApplicationProfileConfig applicationProfileConfig;

  @Test
  void test_single_collapse_application_profile() {
    // given
    Resource resource = new ClassPathResource("model/basic/collapse-single-ap.ap.json");

    // when
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer().apply(resource);
    ApplicationProfile collapse = new CollapseApplicationProfile().apply(applicationProfile);

    // then
    assertEquals(3, applicationProfile.getTypes().size());
    assertEquals(3, collapse.getTypes().size());
  }

}