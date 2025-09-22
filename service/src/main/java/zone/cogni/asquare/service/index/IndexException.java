package zone.cogni.asquare.service.index;

import zone.cogni.libs.core.CognizoneException;

public class IndexException extends CognizoneException {

  public IndexException(String message) {
    super(message);
  }

  public IndexException(String message, Object... parameters) {
    super(message, parameters);
  }

}
