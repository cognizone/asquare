package zone.cogni.actionlogger;

public class ServiceWithLoggers {

  @LoggedAction
  public void simple() {
    try {
      Thread.sleep(10);
    }
    catch (InterruptedException ignored) {
    }
  }

  @LoggedAction("simple")
  public void simple(@LoggedActionRequestor String requestor) {
    try {
      Thread.sleep(10);
    }
    catch (InterruptedException ignored) {
    }
  }

  @LoggedAction
  public void addInfo(String key, Object value) {
    LoggedActionModel.getActionInfoThreadLocal().get().put(key, value);
  }

}
