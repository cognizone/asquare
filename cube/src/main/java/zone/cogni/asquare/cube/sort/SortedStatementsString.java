package zone.cogni.asquare.cube.sort;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

public class SortedStatementsString implements Function<Model, String> {

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String base;
    private Map<String, String> namespaces;
    private int indent = 8;

    public Builder withBase(String base) {
      this.base = base;
      return this;
    }

    public Builder withNamespaces(Map<String, String> namespaces) {
      this.namespaces = namespaces;
      return this;
    }

    public Builder withIndent(int indent) {
      if (indent < 0) throw new RuntimeException("cannot have negative indent");

      this.indent = indent;
      return this;
    }

    public SortedStatementsString build() {
      SortedStatementsString result = new SortedStatementsString();

      if (StringUtils.isNotBlank(base))
        result.setBase(base);

      if (MapUtils.isNotEmpty(namespaces))
        result.setNamespaces(namespaces);

      result.setIndent(indent);

      return result;
    }

  }

  private String base;
  private final Map<String, String> namespaces = new TreeMap<>();
  private int indent;

  public SortedStatementsString() {
  }

  public String getBase() {
    return base;
  }

  public void setBase(String base) {
    this.base = base;
  }

  public Map<String, String> getNamespaces() {
    return namespaces;
  }

  public void setNamespaces(Map<String, String> namespaces) {
    Objects.requireNonNull(namespaces, "namespaces cannot be null");

    if (!this.namespaces.isEmpty())
      this.namespaces.clear();

    addNamespaces(namespaces);
  }

  public void addNamespaces(Map<String, String> namespaces) {
    this.namespaces.putAll(namespaces);
  }

  public int getIndent() {
    return indent;
  }

  public void setIndent(int indent) {
    this.indent = indent;
  }

  @Override
  public String apply(Model model) {
    List<Statement> statements = new StatementSorter().apply(model);
    return apply(statements);
  }

  public String apply(List<Statement> statements) {
    StringBuilder result = new StringBuilder();

    addBase(result);
    addNamespaces(result);
    statementMapToString(result, getStatementMap(statements));

    return result.toString().trim();
  }

  private void addBase(StringBuilder result) {
    if (StringUtils.isBlank(base)) return;

    result.append("@base <").append(base).append("> .\n\n");
  }

  private void addNamespaces(StringBuilder result) {
    if (namespaces.isEmpty()) return;

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
      String predicate = statement.getPredicate().equals(RDF.type) ? "a"
                                                                   : getStringValue(statement.getPredicate());

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
    int maxLength = predicateMap.keySet()
                                .stream()
                                .mapToInt(String::length)
                                .max()
                                .getAsInt();
    predicateMap.forEach((predicate, objects) -> {
      result.append(StringUtils.repeat(' ', indent))
            .append(StringUtils.rightPad(predicate, maxLength + 1));

      objectListToString(result, objects, indent + maxLength + 1);
    });
  }

  private void objectListToString(StringBuilder result, List<String> objects, int indent) {
    for (int i = 0; i < objects.size(); i++) {
      if (i != 0) result.append(StringUtils.repeat(' ', indent));

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

    if (base != null && shortResult.startsWith(base))
      return "<" + StringUtils.substringAfter(shortResult, base) + ">";

    for (Map.Entry<String, String> entry : namespaces.entrySet()) {
      if (!shortResult.startsWith(entry.getValue())) continue;

      String localName = StringUtils.substringAfter(shortResult, entry.getValue());
      if (localName.contains("/") || localName.contains("#")) continue;

      return entry.getKey() + ":" + localName;
    }
    return null;
  }
}
