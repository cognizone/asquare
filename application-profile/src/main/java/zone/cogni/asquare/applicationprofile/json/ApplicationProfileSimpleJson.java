package zone.cogni.asquare.applicationprofile.json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

import java.util.function.Function;

public class ApplicationProfileSimpleJson implements Function<ApplicationProfile, JsonNode> {

  @Override
  public JsonNode apply(ApplicationProfile applicationProfile) {
    JsonMapper objectMapper = JsonMapper.builder().build();
    String json = objectMapper.writeValueAsString(applicationProfile);
    return objectMapper.readTree(json);
  }

}
