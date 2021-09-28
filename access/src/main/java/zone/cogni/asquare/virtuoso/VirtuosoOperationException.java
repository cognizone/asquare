package zone.cogni.asquare.virtuoso;

public class VirtuosoOperationException extends RuntimeException {

  private static final long serialVersionUID = -2605844968942214128L;

  public VirtuosoOperationException() {
  }

  public VirtuosoOperationException(String message) {
    super(message);
  }

  public VirtuosoOperationException(Throwable cause) {
    super(cause);
  }
}
