package zone.cogni.asquare.cube.operation;

import java.util.Collections;
import java.util.List;

public class OperationConfiguration {

  private static final List<String> OutputMinimal = Collections.emptyList();

  private List<String> output = OutputMinimal;
  private boolean securityEnabled = false;

  public List<String> getOutput() {
    return output;
  }

  public void setOutput(List<String> output) {
    this.output = output;
  }

  public boolean isSecurityEnabled() {
    return securityEnabled;
  }

  public void setSecurityEnabled(boolean securityEnabled) {
    this.securityEnabled = securityEnabled;
  }

  public boolean mustOutput(String variable) {
    return output.contains(variable);
  }
}
