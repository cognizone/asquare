package zone.cogni.sem.jena.model;

public class RdfBooleanNodeDto extends RdfNodeDto<Boolean> {

  public static RdfBooleanNodeDto create(Boolean value) {
    RdfBooleanNodeDto node = new RdfBooleanNodeDto();
    node.setValue(value);
    return node;
  }

  public String getStringValue() {
    return getValue() ? "true" : "false";
  }
}
