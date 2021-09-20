package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;

import javax.annotation.Nullable;
import java.util.Objects;

public class PropertyValue implements Rule, Comparable<Rule> {

  private String property;
  private String value;

  public PropertyValue() {
  }

  public PropertyValue(String property, String value) {
    this.property = property;
    this.value = value;
  }

  public String getProperty() {
    return property;
  }

  public void setProperty(String property) {
    this.property = property;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getProperty(), getValue());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PropertyValue)) return false;

    return compareTo((PropertyValue) o) == 0;
  }

  @Override
  public int compareTo(@Nullable Rule other) {
    if (this == other) return 0;
    if (other == null) return -1;

    if (!Objects.equals(getClass(), other.getClass())) return getClass().getName().compareTo(other.getClass().getName());

    PropertyValue otherPropertyValue = (PropertyValue) other;
    int result = property.compareTo(otherPropertyValue.property);
    if (result != 0) return result;

    return value.compareTo(otherPropertyValue.value);
  }

  @Override
  public String toString() {
     return "PropertyValue{" +
            "property='" + property + '\'' +
            ", value='" + value + '\'' +
            '}';
  }

  @Override
  public PropertyValue copy() {
    return new PropertyValue(property, value);
  }
}

