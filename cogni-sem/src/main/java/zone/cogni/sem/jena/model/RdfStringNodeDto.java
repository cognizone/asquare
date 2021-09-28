package zone.cogni.sem.jena.model;

public class RdfStringNodeDto extends RdfNodeDto<String> {

  public static RdfStringNodeDto create(String value) {
    RdfStringNodeDto node = new RdfStringNodeDto();
    node.setValue(value);
    return node;
  }

  public static RdfStringNodeDto create(String value, String name, String type) {
    RdfStringNodeDto node = new RdfStringNodeDto();
    node.setValue(value);
    node.setName(name);
    node.setType(type);
    return node;
  }
}
