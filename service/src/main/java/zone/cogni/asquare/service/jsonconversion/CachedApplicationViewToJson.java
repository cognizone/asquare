package zone.cogni.asquare.service.jsonconversion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Supplier;

public class CachedApplicationViewToJson implements Supplier<ObjectNode> {

  private static final Logger log = LoggerFactory.getLogger(CachedApplicationViewToJson.class);

  private final Object lock = new Object();

  private ApplicationViewToJson applicationViewToJson;
  private final File file;

  public CachedApplicationViewToJson(ApplicationViewToJson applicationViewToJson, File file) {
    this.applicationViewToJson = applicationViewToJson;
    this.file = file;
  }

  @Override
  public ObjectNode get() {
    log.info(".. .. .. calculating ...{}", StringUtils.right(file.getPath(), 45));
    synchronized (lock) {
      if (applicationViewToJson != null && !file.exists()) {
        ObjectNode jsonNodes = applicationViewToJson.get();

        writeFile(jsonNodes);

        applicationViewToJson = null;
        return jsonNodes;
      }
    }

    log.info(".. .. .. reading ...{}.", StringUtils.right(file.getPath(), 45));
    try {
      return (ObjectNode) new ObjectMapper().readTree(file);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeFile(ObjectNode jsonNodes) {
    try {
      new ObjectMapper().writeValue(new FileWriter(file), jsonNodes);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
