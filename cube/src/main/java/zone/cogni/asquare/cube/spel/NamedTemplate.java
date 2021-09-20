package zone.cogni.asquare.cube.spel;

import org.springframework.core.io.Resource;
import zone.cogni.core.spring.ResourceHelper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NamedTemplate {

  /**
   * might not be stable: uses filename for name !!
   */
  public static List<NamedTemplate> fromResources(Resource... resources) {
    return Arrays.stream(resources)
                 .map(NamedTemplate::fromResource)
                 .collect(Collectors.toList());
  }

  public static NamedTemplate fromResource(Resource resource) {
    return fromResource(resource, resource.getFilename());
  }

  public static NamedTemplate fromResource(Resource resource, String name) {
    return fromString(ResourceHelper.toString(resource), name);
  }

  public static NamedTemplate fromString(String template, String name) {
    return new NamedTemplate(template, name);
  }

  private final String template;
  private final String name;

  private Object root;
  private String result;

  protected NamedTemplate(String template, String name) {
    this.template = template;
    this.name = name;
  }

  /**
   * @return copy of template part, not result part
   */
  public NamedTemplate copy() {
    return new NamedTemplate(template, name);
  }

  public String getTemplate() {
    return template;
  }

  public String getName() {
    return name;
  }

  public Object getRoot() {
    return root;
  }

  public void setRoot(Object root) {
    this.root = root;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }
}
