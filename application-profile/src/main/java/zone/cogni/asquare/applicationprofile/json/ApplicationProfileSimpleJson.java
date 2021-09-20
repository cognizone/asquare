package zone.cogni.asquare.applicationprofile.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

import java.io.IOException;
import java.util.function.Function;

public class ApplicationProfileSimpleJson implements Function<ApplicationProfile, JsonNode> {

  @Override
  public JsonNode apply(ApplicationProfile applicationProfile) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      String json = objectMapper.writeValueAsString(applicationProfile);
      return objectMapper.readTree(json);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
