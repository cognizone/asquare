package zone.cogni.asquare.cube.jsondiff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonDiffCalculator implements Function<JsonDiffInput, List<Difference>> {

  private static final Logger log = LoggerFactory.getLogger(JsonDiffCalculator.class);

  private final Configuration configuration;

  public JsonDiffCalculator(Configuration configuration) {
    this.configuration = configuration;
  }

  @Override
  public List<Difference> apply(JsonDiffInput jsonDiffInput) {
    if (jsonDiffInput.getFrom() == null || jsonDiffInput.getTo() == null)
      throw new RuntimeException("invalid difference calculation: one of objects is null");

    List<Difference> differences = new ArrayList<>();
    JsonNode from = jsonDiffInput.getFrom().get();
    JsonNode to = jsonDiffInput.getTo().get();
//    System.out.println("from = \n" + from);
//    System.out.println("to = \n" + to);
    compare(differences, from, to, "");
    return differences;
  }

  private void compare(List<Difference> differences,
                       JsonNode from,
                       JsonNode to,
                       String context) {
    try {
      boolean sameNodeType = from.getNodeType().equals(to.getNodeType());
      if (!sameNodeType) {
        Difference difference = new Difference();
        difference.setType(Difference.Type.different_node_types);
        difference.setPath(context);
        difference.setMessage("from type '" + from.getNodeType() + "' and to type '" + to
                .getNodeType() + "'");

        differences.add(difference);
        return;
      }

    }
    catch (Exception e) {
      throw e;
    }

    if (from.getNodeType().equals(JsonNodeType.ARRAY)) {
      compareArray(differences, from, to, context);
      return;
    }

    if (from.getNodeType().equals(JsonNodeType.BINARY))
      throw new RuntimeException(JsonNodeType.BINARY.name() + " node type not supported");

    if (from.getNodeType().equals(JsonNodeType.BOOLEAN)) {
      compareBoolean(differences, from, to, context);
      return;
    }

    if (from.getNodeType().equals(JsonNodeType.MISSING))
      throw new RuntimeException(JsonNodeType.MISSING.name() + " node type not supported");

    if (from.getNodeType().equals(JsonNodeType.NULL)) return;

    if (from.getNodeType().equals(JsonNodeType.NUMBER)) {
      compareNumber(differences, from, to, context);
      return;
    }

    if (from.getNodeType().equals(JsonNodeType.OBJECT)) {
      compareObject(differences, from, to, context);
      return;
    }

    if (from.getNodeType().equals(JsonNodeType.POJO))
      throw new RuntimeException(JsonNodeType.POJO.name() + " node type not supported");

    if (from.getNodeType().equals(JsonNodeType.STRING)) {
      compareString(differences, from, to, context);
      return;
    }
  }

  private void compareArray(List<Difference> differences, JsonNode from, JsonNode to, String context) {
    ArrayNode fromArray = (ArrayNode) from;
    ArrayNode toArray = (ArrayNode) to;

    boolean fromArrayEmpty = fromArray.size() == 0;
    boolean toArrayEmpty = toArray.size() == 0;
    if (fromArrayEmpty && toArrayEmpty) {
      // all fine
      return;
    }

    if (fromArray.size() != toArray.size()) {
      Difference difference = new Difference();
      difference.setType(Difference.Type.different_array_size);
      difference.setPath(context);
      difference.setMessage("from array has " + fromArray.size() + " elements"
                            + " and to array has " + toArray.size() + " elements");
      differences.add(difference);
    }

    if (configuration.isPreserveArrayOrder()) {
      compareArrayElements(differences, context, fromArray, toArray);
      return;
    }

    Set<JsonNodeType> nodeTypes = getNodeTypes(from, to);
    if (nodeTypes.size() > 1) {
      // too difficult for now
      log.warn("[{}] running an ordered compare for type {}", context, nodeTypes);
      compareArrayElements(differences, context, fromArray, toArray);
      return;
    }

    if (nodeTypes.size() != 1)
      throw new RuntimeException("expected 1 node type here: found " + nodeTypes);

    JsonNodeType nodeType = nodeTypes.stream().findFirst().get();
    if (nodeType.equals(JsonNodeType.STRING)) {
      reportCollectionIssues(differences,
                             context,
                             getStringCollection(fromArray),
                             getStringCollection(toArray));
      return;
    }
    if (nodeType.equals(JsonNodeType.BOOLEAN)) {
      reportCollectionIssues(differences,
                             context,
                             getBooleanCollection(fromArray),
                             getBooleanCollection(toArray));
      return;
    }
    if (nodeType.equals(JsonNodeType.NUMBER)) {
      // too difficult for now => should be improved later
      log.warn("[{}] running an ordered compare for type {}", context, nodeType);
      compareArrayElements(differences, context, fromArray, toArray);
      return;
    }

    if (nodeType.equals(JsonNodeType.OBJECT) && configuration.getComparator() != null) {
      pairArrays(differences, context, fromArray, toArray);
      return;
    }

    // too difficult for now
    log.warn("[{}] running an ordered compare for type {}", context, nodeType);
    compareArrayElements(differences, context, fromArray, toArray);
  }

  private void reportCollectionIssues(List<Difference> differences, String context, Set<?> fromStrings, Set<?> toStrings) {
    if (CollectionUtils.isEqualCollection(fromStrings, toStrings)) {
      return;
    }

    Collection<?> onlySource = CollectionUtils.subtract(fromStrings, toStrings);
    if (!onlySource.isEmpty()) {
      Difference difference = new Difference();
      difference.setType(Difference.Type.element_only_in_from);
      difference.setPath(context);
      difference.setMessage("from collection contains elements not in to collection: " + onlySource);
      differences.add(difference);
    }

    Collection<?> onlyTarget = CollectionUtils.subtract(toStrings, fromStrings);
    if (!onlyTarget.isEmpty()) {
      Difference difference = new Difference();
      difference.setType(Difference.Type.element_only_in_to);
      difference.setPath(context);
      difference.setMessage("to collection contains elements not in from collection: " + onlyTarget);
      differences.add(difference);
    }
  }

  private void pairArrays(List<Difference> differences, String context, ArrayNode fromArray, ArrayNode toArray) {
    getPairs(differences, context, fromArray, toArray)
            .forEach(pair -> {
              compare(differences, pair.fromNode, pair.toNode, pair.fromContext);
            });
  }

  /**
   * @return find matching pairs of objects... typically based on ids.
   */
  private List<ArrayElementPair> getPairs(List<Difference> differences,
                                          String context,
                                          ArrayNode fromArray,
                                          ArrayNode toArray) {
    List<ArrayElementPair> result = new ArrayList<>();

    Set<Integer> usedFromPositions = new HashSet<>();
    Set<Integer> usedToPositions = new HashSet<>();

    for (int i = 0; i < fromArray.size(); i++) {
      ObjectNode fromNode = (ObjectNode) fromArray.get(i);
      for (int j = 0; j < toArray.size(); j++) {
        ObjectNode toNode = (ObjectNode) toArray.get(j);
        if (configuration.getComparator().apply(fromNode, toNode)) {
          usedFromPositions.add(i);
          usedToPositions.add(j);

          String fromId = configuration.getId().apply(fromNode);
          String toId = configuration.getId().apply(toNode);
          result.add(new ArrayElementPair(fromNode,
                                          context + "[" + fromId + "]",
                                          toNode,
                                          context + "[" + toId + "]"));
          break;
        }
      }
    }

    List<Integer> unusedFromPositions = getUnusedPositions(fromArray, usedFromPositions);
    unusedFromPositions.forEach(position -> {
      JsonNode node = fromArray.get(position);
      String value = node.isObject() ? configuration.getId().apply((ObjectNode) node)
                                     : Integer.toString(position);
      String differenceContext = context + "[" + value + "]";

      Difference difference = new Difference();
      difference.setType(Difference.Type.element_only_in_from);
      difference.setPath(differenceContext);
      difference.setMessage("from array position not found in to array");
      differences.add(difference);
    });

    List<Integer> unusedToPositions = getUnusedPositions(toArray, usedToPositions);
    unusedToPositions.forEach(position -> {
      JsonNode node = toArray.get(position);
      String value = node.isObject() ? configuration.getId().apply((ObjectNode) node)
                                     : Integer.toString(position);
      String differenceContext = context + "[" + value + "]";

      Difference difference = new Difference();
      difference.setType(Difference.Type.element_only_in_to);
      difference.setPath(differenceContext);
      difference.setMessage("element only in to array");
      differences.add(difference);
    });

    return result;
  }

  private List<Integer> getUnusedPositions(ArrayNode array, Set<Integer> usedPositions) {
    return Stream.iterate(0, i -> i + 1)
                 .limit(array.size())
                 .filter(value -> !usedPositions.contains(value))
                 .collect(Collectors.toList());
  }


  private static class ArrayElementPair {
    private ObjectNode fromNode;
    private String fromContext;
    private ObjectNode toNode;
    private String toContext;

    public ArrayElementPair(ObjectNode fromNode, String fromContext, ObjectNode toNode, String toContext) {
      this.fromNode = fromNode;
      this.fromContext = fromContext;
      this.toNode = toNode;
      this.toContext = toContext;
    }
  }

  private Set<String> getStringCollection(ArrayNode array) {
    Set<String> result = new HashSet<>();
    for (int i = 0; i < array.size(); i++) {
      result.add(array.get(i).textValue());
    }
    return result;
  }

  private Set<Boolean> getBooleanCollection(ArrayNode array) {
    Set<Boolean> result = new HashSet<>();
    for (int i = 0; i < array.size(); i++) {
      result.add(array.get(i).booleanValue());
    }
    return result;
  }

  private Set<JsonNodeType> getNodeTypes(JsonNode from, JsonNode to) {
    Set<JsonNodeType> result = new HashSet<>();

    for (int i = 0; i < from.size(); i++) {
      result.add(from.get(i).getNodeType());
    }
    for (int i = 0; i < to.size(); i++) {
      result.add(to.get(i).getNodeType());
    }

    return result;
  }

  private void compareArrayElements(List<Difference> differences, String context, ArrayNode fromArray, ArrayNode toArray) {
    for (int i = 0; i < Math.min(fromArray.size(), toArray.size()); i++) {
      compare(differences,
              fromArray.get(i),
              toArray.get(i),
              context + "[" + i + "]");
    }
  }

  private void compareBoolean(List<Difference> differences, JsonNode from, JsonNode to, String context) {
    boolean fromBoolean = from.asBoolean();
    boolean toBoolean = to.asBoolean();
    if (fromBoolean != toBoolean && fieldMustMatch(context)) {
      Difference difference = new Difference();
      difference.setType(Difference.Type.different_boolean_values);
      difference.setPath(context);
      difference
              .setMessage("from boolean has value " + fromBoolean + " and to boolean has value " + toBoolean);

      differences.add(difference);
    }
  }

  private void compareNumber(List<Difference> differences, JsonNode from, JsonNode to, String context) {
    if (from.isIntegralNumber() && to.isIntegralNumber()) {
      compareLongs(differences, from, to, context);
      return;
    }

    if (from.isFloatingPointNumber() || to.isFloatingPointNumber()) {
      compareDoubles(differences, from, to, context);
      return;
    }

    throw new RuntimeException("compare of numbers went wrong: " + from + " and " + to);
  }

  private void compareLongs(List<Difference> differences, JsonNode from, JsonNode to, String context) {
    long fromLong = from.asLong();
    long toLong = to.asLong();
    if (fromLong != toLong && fieldMustMatch(context)) {
      Difference difference = new Difference();
      difference.setType(Difference.Type.different_integer_values);
      difference.setPath(context);
      difference.setMessage("from integer has value " + fromLong + " and to integer has value " + toLong);

      differences.add(difference);
    }
  }

  private boolean fieldMustMatch(String context) {
    String field = StringUtils.substringAfterLast(context, ".");
    return !configuration.getPropertiesWithDeviatingValues().contains(field);
  }

  private void compareDoubles(List<Difference> differences, JsonNode from, JsonNode to, String context) {
    double fromDouble = from.asDouble();
    double toDouble = to.asDouble();
    double delta = Math.abs(fromDouble - toDouble);
    if (delta > 0.0000001 && fieldMustMatch(context)) {
      Difference difference = new Difference();
      difference.setType(Difference.Type.different_decimal_values);
      difference.setPath(context);
      difference.setMessage("from decimal has value " + from.asText() + " and to decimal has value " + to.asText());

      differences.add(difference);
    }
  }

  private void compareObject(List<Difference> differences, JsonNode from, JsonNode to, String context) {
    ObjectNode fromObject = (ObjectNode) from;
    ObjectNode toObject = (ObjectNode) to;

    List<String> fromFields = IteratorUtils.toList(fromObject.fieldNames());
    List<String> toFields = IteratorUtils.toList(toObject.fieldNames());

    Collection<String> sourceOnly = CollectionUtils.removeAll(fromFields, toFields);
    sourceOnly.forEach(field -> {
      Difference difference = new Difference();
      difference.setType(Difference.Type.field_only_in_from);
      difference.setPath(context + "." + field);
      difference.setMessage("field only present in from");
      differences.add(difference);
    });

    Collection<String> targetOnly = CollectionUtils.removeAll(toFields, fromFields);
    targetOnly.forEach(field -> {
      Difference difference = new Difference();
      difference.setType(Difference.Type.field_only_in_to);
      difference.setPath(context + "." + field);
      difference.setMessage("field only present in to");
      differences.add(difference);
    });

    Collection<String> commonFields = CollectionUtils.intersection(toFields, fromFields);
    commonFields.forEach(field -> {
      compare(differences, from.get(field), to.get(field), context + "." + field);
    });
  }

  private void compareString(List<Difference> differences, JsonNode from, JsonNode to, String context) {
    String fromString = from.textValue();
    String toString = to.textValue();

    if (!fromString.equals(toString) && fieldMustMatch(context)) {
      Difference difference = new Difference();
      difference.setType(Difference.Type.different_string_values);
      difference.setPath(context);
      difference.setMessage("from string has value '" + fromString + "'"
                            + " and to string has value '" + toString + "'");

      differences.add(difference);
    }
  }

}
