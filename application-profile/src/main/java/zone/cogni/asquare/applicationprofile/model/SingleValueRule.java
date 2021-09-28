package zone.cogni.asquare.applicationprofile.model;

import java.util.Arrays;

public abstract class SingleValueRule<T> implements Rule {

  public static <T> T getSingleValueCopy(T el) {
    if (Rule.class.isAssignableFrom(el.getClass())) {
      Rule copy = ((Rule) el).copy();
      return (T) copy;
    }

    Class<?> type = el.getClass();
    if (Arrays.asList(String.class, Integer.class, Long.class).contains(type)) {
      return el;
    }

    throw new RuntimeException("Unsupported copy of element of type " + type.getName());
  }

  private T value;

  protected SingleValueRule() {
  }

  protected SingleValueRule(T value) {
    this.value = value;
  }

  public T getValue() {
    return value;
  }

  protected T getValueCopy() {
    return getSingleValueCopy(value);
  }

  public void setValue(T value) {
    if (!canSet(value))
      throw new IllegalStateException("ApplicationProfile.Type of value is " + value.getClass().getName() + " and allowed type is " + allowedType().getName());

    this.value = value;
  }

  public abstract Class<T> allowedType();

  public boolean canSet(T value) {
    return value == null || allowedType().isAssignableFrom(value.getClass());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SingleValueRule<?> that = (SingleValueRule<?>) o;

    return value != null ? value.equals(that.value) : that.value == null;
  }

  @Override
  public int hashCode() {
    return value != null ? value.hashCode() : 0;
  }

  @Override
  public String toString() {
    return getRuleName() + "{ value:" + value + " }";
  }
}
