package zone.cogni.asquare.elastic.access;

import com.google.common.base.Preconditions;
import zone.cogni.asquare.rdf.TypedResource;

import java.util.List;

@Deprecated
public class PagedResultSet<T extends TypedResource> {
  private PageParams pageParams = new PageParams();
  private List<T> resources;

  public PagedResultSet() {
  }

  public PagedResultSet(PageParams pageParams, List<T> resources) {
    Preconditions.checkNotNull(resources);
    Preconditions.checkNotNull(pageParams);

    this.pageParams = pageParams;
    this.resources = resources;
  }

  public long getTotal() {
    return pageParams.getTotal();
  }

  public int getPage() {
    return pageParams.getPage();
  }

  public int getSize() {
    return pageParams.getSize();
  }

  public List<T> getResources() {
    return resources;
  }

  public PagedResultSet<T> setResources(List<T> resources) {
    Preconditions.checkNotNull(resources);
    this.resources = resources;
    return this;
  }

  public PagedResultSet setPageParams(PageParams pageParams) {
    Preconditions.checkNotNull(pageParams);
    this.pageParams = pageParams;
    return this;
  }

  public PageParams getPageParams() {
    return pageParams;
  }

}
