package zone.cogni.asquare.cube.sync;

import org.apache.commons.lang3.StringUtils;

public class SelectedGraph {

  private String graphUri;
  private String modificationStamp;

  public SelectedGraph(String graphUri, String modificationStamp) {
    if (graphUri == null) throw new RuntimeException("graphUri cannot be null");

    this.graphUri = graphUri;
    this.modificationStamp = modificationStamp;
  }

  public boolean isMissingModificationStamp() {
    return !hasModificationStamp();
  }

  public boolean hasModificationStamp() {
    return StringUtils.isNotBlank(modificationStamp);
  }

  public String getGraphUri() {
    return graphUri;
  }

  public void setGraphUri(String graphUri) {
    this.graphUri = graphUri;
  }

  public String getModificationStamp() {
    return modificationStamp;
  }

  public void setModificationStamp(String modificationStamp) {
    this.modificationStamp = modificationStamp;
  }
}
