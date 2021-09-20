package zone.cogni.asquare.service.queryapi.filter;

public abstract class AbstractDepthFilterPipe implements DepthFilterPipe {

  protected final int startDepth;
  protected final int endDepth;
  private final String filterClass;


 public AbstractDepthFilterPipe(Integer startDepth, Integer endDepth) {
    this.startDepth = startDepth == null ? 0 : startDepth;
    this.endDepth = endDepth == null ? Integer.MAX_VALUE : endDepth;
    this.filterClass = this.getClass().getCanonicalName();
  }

  public AbstractDepthFilterPipe(Integer startDepth) {
    this.startDepth =  startDepth == null ? 0 : startDepth;
    this.endDepth = Integer.MAX_VALUE;
    this.filterClass = this.getClass().getCanonicalName();
  }

  public AbstractDepthFilterPipe() {
    this.startDepth = 0;
    this.endDepth = Integer.MAX_VALUE;
    this.filterClass = this.getClass().getCanonicalName();
  }

  @Override
  public boolean isActiveForDepth(int depth) {
    return depth >= startDepth && depth < endDepth;
  }

  public String getFilterClass() {
    return filterClass;
  }

  public int getStartDepth() {
    return startDepth;
  }

  public int getEndDepth() {
    return endDepth;
  }
}
