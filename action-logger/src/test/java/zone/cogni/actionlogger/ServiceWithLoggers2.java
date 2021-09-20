package zone.cogni.actionlogger;

import javax.inject.Inject;

public class ServiceWithLoggers2 {

  @Inject
  private ServiceWithLoggers serviceWithLoggers;

  @LoggedAction
  public void withParent() { //has to be in other class, Proxy doesn't pick up inner-class calls :(
    try {
      Thread.sleep(10);
    }
    catch (InterruptedException ignored) {
    }
    serviceWithLoggers.simple();
    try {
      Thread.sleep(10);
    }
    catch (InterruptedException ignored) {
    }
  }
}
