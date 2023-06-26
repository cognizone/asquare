package zone.cogni.asquare.virtuoso;

public class VirtuosoOperationException extends RuntimeException {

  // something to additionally show or not as needed
  private String longMessage;

  private static final long serialVersionUID = -2605844968942214128L;

  public VirtuosoOperationException() {
  }

  public VirtuosoOperationException(String message) {
    super(message);
  }

  public VirtuosoOperationException(Throwable cause) {
    super(cause);
  }

  public VirtuosoOperationException(String shortMessage, String longMessage) {
    super(shortMessage);
    this.longMessage = longMessage;
  }

  public String getLongMessage() {
    return longMessage;
  }
}
