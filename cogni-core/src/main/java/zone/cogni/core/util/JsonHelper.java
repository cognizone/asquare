package zone.cogni.core.util;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;


public class JsonHelper {

  private static final JsonMapper objectMapper = JsonMapper.builder()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  public static String toJson(Object object) {
    return objectMapper.writeValueAsString(object);
  }
}
