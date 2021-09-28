package zone.cogni.actionlogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Map;
import java.util.Objects;

public interface LoggedActionSaver {

  void save(Map<String, Object> report);

  default ObjectNode convertToObjectNode(Map<String, Object> report) {
    try {
      return new ObjectMapper().valueToTree(report);
    }
    catch (Exception e) {
      // TODO is this not weird...?
      //  if conversions go wrong we do not log action but conversion errors?
      ObjectNode objectNode = new ObjectMapper().createObjectNode();
      objectNode.put("message", "Convert report to ObjectNode failed");
      objectNode.put("exception", e.getMessage());
      objectNode.put("stackTrace", ExceptionUtils.getStackTrace(e));
      objectNode.put("reportToString", Objects.toString(report));
      return objectNode;
    }
  }
}