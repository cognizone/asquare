package zone.cogni.core.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;


public class JsonHelper {

  private static final JsonMapper objectMapper = JsonMapper.builder()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  public static String toJson(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    }
    catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }
}
