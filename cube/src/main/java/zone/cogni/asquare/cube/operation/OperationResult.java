package zone.cogni.asquare.cube.operation;

public class OperationResult {

  private final Operation operation;
  private final String uri;
  private boolean enabled;

  public OperationResult(Operation operation, String uri) {
    this.operation = operation;
    this.uri = uri;
  }

  public Operation getOperation() {
    return operation;
  }

  public String getUri() {
    return uri;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
