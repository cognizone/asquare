package zone.cogni.asquare.elastic.access;

@Deprecated
public class PageParams {
  private long total;
  private int page;
  private int size;

  public PageParams() {
  }

  public PageParams(PageParams pageParams) {
    this(pageParams.getTotal(), pageParams.getPage(), pageParams.getSize());
  }

  public PageParams(long total, int page, int size) {
    this.total = total;
    this.page = page;
    this.size = size;
  }

  public PageParams setTotal(long total) {
    this.total = total;
    return this;
  }

  public PageParams setPage(int page) {
    this.page = page;
    return this;
  }

  public PageParams setSize(int size) {
    this.size = size;
    return this;
  }

  public long getTotal() {
    return total;
  }

  public int getPage() {
    return page;
  }

  public int getSize() {
    return size;
  }
}
