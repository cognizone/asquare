package zone.cogni.service.extfolder;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

public class SynchronizationInfo {
  private final String source;
  private final String destination;
  private final boolean resetAtStartup;

  public SynchronizationInfo(String source) {
    this(source, null, false);
  }

  public SynchronizationInfo(String source, boolean resetAtStartup) {
    this(source, null, resetAtStartup);
  }

  public SynchronizationInfo(String source, String destination) {
    this(source, destination, false);
  }

  public SynchronizationInfo(String source, String destination, boolean resetAtStartup) {
    Preconditions.checkState(StringUtils.isNotBlank(source), "Source of a SynchronizationInfo can't be blank.");
    this.source = source;
    this.destination = destination == null ? "" : destination;
    this.resetAtStartup = resetAtStartup;
  }

  public String getSource() {
    return source;
  }

  public String getDestination() {
    return destination;
  }

  public boolean isResetAtStartup() {
    return resetAtStartup;
  }
}
