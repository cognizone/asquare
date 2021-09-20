package zone.cogni.asquare.cube.jsondiff;

import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;

public class Difference {

  public static String getDifferenceReport(JsonDiffInput input, List<Difference> differences) {
    differences.sort(Comparator.comparing(difference -> difference.path));

    Integer maxPath = differences.stream()
                                 .map(difference -> difference.path.length())
                                 .max(Comparator.naturalOrder())
                                 .orElse(0);

    StringBuilder result = new StringBuilder();
    result.append("ID ").append(input.getId())
          .append(": ")
          .append(differences.size()).append(" issues found\n");

    differences.forEach(difference -> {
      result.append(StringUtils.rightPad(difference.path, maxPath))
            .append(" ").append(StringUtils.rightPad(difference.type.name(), 22))
            .append(" - ").append(difference.message).append("\n");
    });

    return result.toString();
  }

  public enum Type {
    different_node_types,
    different_array_size,
    different_boolean_values,
    different_integer_values,
    different_decimal_values,
    field_only_in_from,
    field_only_in_to,
    different_string_values,
    element_only_in_from,
    element_only_in_to
  }

  private Type type;
  private String path;
  private String message;

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
