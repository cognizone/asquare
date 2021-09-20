package zone.cogni.asquare.applicationprofile.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class ListSingleValueRule<T> implements Rule {

  private List<T> value;

  protected ListSingleValueRule() {
  }

  protected ListSingleValueRule(List<T> value) {
    this.value = value;
  }

  public List<T> getValue() {
    if (value == null) value = new ArrayList<>();
    return value;
  }

  protected List<T> getValueCopy() {
    return getValue().stream()
                     .map(SingleValueRule::getSingleValueCopy)
                     .collect(Collectors.toList());
  }

  public void setValue(List<T> list) {
    Objects.requireNonNull(list);
    if (!canSet(list))
      throw new IllegalStateException("ApplicationProfile.Type problem in list: " + list);

    value = list;
    sort();
  }

  private void sort() {
    boolean sortable = value.stream().allMatch(element -> Comparable.class.isAssignableFrom(element.getClass()));
    if (!sortable) return;

    List<Comparable<Comparable>> comparables = (List<Comparable<Comparable>>) value;
    Collections.sort(comparables);
  }

  public boolean canSet(List<T> list) {
    return list.stream().allMatch(element -> allowedType().isAssignableFrom(element.getClass()));
  }

  public abstract Class<T> allowedType();

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ListSingleValueRule<?> that = (ListSingleValueRule<?>) o;

    return value != null ? value.equals(that.value) : that.value == null;
  }

  @Override
  public int hashCode() {
    return value != null ? value.hashCode() : 0;
  }

  @Override
  public String toString() {
    return getRuleName() + "{" +
           "values=[" + value.stream().map(Object::toString).collect(Collectors.joining(", ")) +
           "]}";
  }
}
