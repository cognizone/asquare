package zone.cogni.asquare.cube.json5;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Json5LightTest {

  @Test
  public void read_json() throws IOException {
    // given
    Resource resource = new ClassPathResource("json5/read_json.json5");
    ObjectMapper json5Mapper = Json5Light.getJson5Mapper();

    // when
    JsonNode root = json5Mapper.readTree(resource.getInputStream());

    // then
    assertTrue(root.has("id"));
    assertTrue(root.has("someNumber"));
    assertTrue(root.has("multiLine"));
  }

}