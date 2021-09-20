package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

public class PropertyPath implements Rule {

  private String path;
  private String value;

  public PropertyPath() {
  }

  public PropertyPath(String path, String value) {
    this.path = path;
    this.value = value;
  }

  public String getPath() {
    return path;
  }

  public PropertyPath setPath(String path) {
    this.path = path;
    return this;
  }

  public String getValue() {
    return value;
  }

  public PropertyPath setValue(String value) {
    this.value = value;
    return this;
  }

  @Override
  public PropertyPath copy() {
    return new PropertyPath(path, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PropertyPath that = (PropertyPath) o;

    if (path != null ? !path.equals(that.path) : that.path != null) return false;
    return value != null ? value.equals(that.value) : that.value == null;
  }

  @Override
  public int hashCode() {
    int result = path != null ? path.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return getRuleName() + "{" +
           path + "=" + value +
           '}';
  }
}
