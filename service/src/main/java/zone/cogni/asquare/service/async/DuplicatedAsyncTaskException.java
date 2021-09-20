package zone.cogni.asquare.service.async;

import zone.cogni.libs.core.CognizoneException;

public class DuplicatedAsyncTaskException extends CognizoneException {

  public DuplicatedAsyncTaskException(String message, Object... parameters) {
    super(message, parameters);
  }
}
