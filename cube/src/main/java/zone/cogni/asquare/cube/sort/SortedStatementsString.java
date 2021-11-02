package zone.cogni.asquare.cube.sort;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class SortedStatementsString implements Function<List<Statement>, StringBuilder> {

  private static final int DEFAULT_INDENT = 24;

  private final int indent;
  private final Map<String, String> namespaces = new TreeMap<>();

  public SortedStatementsString() {
    this(DEFAULT_INDENT, ImmutableMap.of());
  }

  public SortedStatementsString(Map<String, String> namespaces) {
    this(DEFAULT_INDENT, namespaces);
  }

  public SortedStatementsString(int indent, Map<String, String> namespaces) {
    this.indent = indent;
    this.namespaces.putAll(namespaces);
  }

  @Override
  public StringBuilder apply(List<Statement> statements) {
    StringBuilder result = new StringBuilder();

    addNamespaces(result);
    statementMapToString(result, getStatementMap(statements));

    return result;
  }

  private void addNamespaces(StringBuilder result) {
    namespaces.forEach((key, value) -> {
      result.append("@prefix ")
            .append(StringUtils.rightPad(key + ":", 8))
            .append("<").append(value).append("> .\n");
    });
    result.append("\n");
  }

  private Map<String, Map<String, List<String>>> getStatementMap(List<Statement> statements) {
    Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();

    statements.forEach(statement -> {
      String subject = getStringValue(statement.getSubject());
      result.putIfAbsent(subject, new LinkedHashMap<>());

      Map<String, List<String>> predicateMap = result.get(subject);
      String predicate = getStringValue(statement.getPredicate());

      predicateMap.putIfAbsent(predicate, new ArrayList<>());
      List<String> objects = predicateMap.get(predicate);

      objects.add(getStringValue(statement.getObject()));
    });

    return result;
  }

  private void statementMapToString(StringBuilder result, Map<String, Map<String, List<String>>> statementMap) {
    statementMap.forEach((subject, predicateMap) -> {
      result.append(subject).append("\n");

      predicateMapToString(result, predicateMap);

      result.append(".\n\n");
    });
  }

  private void predicateMapToString(StringBuilder result, Map<String, List<String>> predicateMap) {
    predicateMap.forEach((predicate, objects) -> {
      result.append(StringUtils.repeat(' ', indent))
            .append(StringUtils.rightPad(predicate, indent - 1));

      objectListToString(result, objects);
    });
  }

  private void objectListToString(StringBuilder result, List<String> objects) {
    for (int i = 0; i < objects.size(); i++) {
      if (i != 0) result.append(StringUtils.repeat(' ', 2 * indent - 1));

      result.append(" ").append(objects.get(i));

      String endChar = i != objects.size() - 1 ? "," : ";";
      result.append(endChar).append("\n");
    }
  }

  @Nonnull
  private String getStringValue(RDFNode rdfNode) {
    String result = (String) rdfNode.visitWith(StringRdfVisitor.instance);

    String shortUri = getShortString(result);
    return shortUri != null ? shortUri : result;
  }

  @Nullable
  private String getShortString(String result) {
    // shorten uri if possible
    boolean isUri = result.charAt(0) == '<' && result.charAt(result.length() - 1) == '>';
    if (isUri) {
      return getShortUri(result);
    }

    // shorten typed literal if possible
    boolean isTypedLiteral = result.charAt(0) == '"'
                             && result.contains("\"^^<")
                             && result.charAt(result.length() - 1) == '>';
    if (isTypedLiteral) {
      String typeUri = StringUtils.substringAfterLast(result, "^^");
      String valueString = StringUtils.substringBeforeLast(result, "^^");

      String shortTypeUri = getShortUri(typeUri);
      return valueString + "^^" + (shortTypeUri == null ? typeUri : shortTypeUri);
    }

    return null;
  }

  private String getShortUri(String fullUri) {
    String shortResult = fullUri.substring(1, fullUri.length() - 1);
    for (Map.Entry<String, String> entry : namespaces.entrySet()) {
      if (!shortResult.startsWith(entry.getValue())) continue;

      String localName = StringUtils.substringAfter(shortResult, entry.getValue());
      if (localName.contains("/") || localName.contains("#")) continue;

      return entry.getKey() + ":" + localName;
    }
    return null;
  }
}
