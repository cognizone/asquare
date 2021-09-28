package zone.cogni.asquare.service.rest.model;

public enum RestResponseMeta {
  SUCCESS("success"), ERROR("error");

  private final String label;

  RestResponseMeta(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return this.label;
  }
}