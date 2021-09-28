package zone.cogni.asquare.access.graph;

import zone.cogni.libs.core.CognizoneException;

public class GraphViewOperationException extends CognizoneException {

  public GraphViewOperationException(String message) {
    super(message);
  }

  public GraphViewOperationException(String message, Object... parameters) {
    super(message, parameters);
  }
}
