package zone.cogni.actionlogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class LogLoggedActionSaver implements LoggedActionSaver {

  private final Logger log;

  public LogLoggedActionSaver() {
    this(LoggerFactory.getLogger(LogLoggedActionSaver.class));
  }

  public LogLoggedActionSaver(Logger log) {
    this.log = log;
  }

  @Override
  public void save(Map<String, Object> report) {
    log.info("LoggedAction: {}", convertToObjectNode(report));
  }
}
