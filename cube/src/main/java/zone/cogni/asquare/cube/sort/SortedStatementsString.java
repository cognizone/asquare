package zone.cogni.asquare.cube.sort;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

public class SortedStatementsString implements Function<List<Statement>, StringBuilder> {

  private final int indent;
  private final boolean compact = true;
  private final Map<String, String> namespaces = new TreeMap<>();

  public SortedStatementsString() {
    this(18, ImmutableMap.of());
  }

  public SortedStatementsString(Map<String, String> namespaces) {
    this(18, namespaces);
  }

  public SortedStatementsString(int indent, Map<String, String> namespaces) {
    this.indent = indent;
    this.namespaces.putAll(namespaces);
  }

  @Override
  public StringBuilder apply(List<Statement> statements) {
    StringBuilder result = new StringBuilder();

    addNamespaces(result);
    addStatements(result, statements);

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

  private void addStatements(StringBuilder result, List<Statement> statements) {
    Resource subject = null;
    Property predicate = null;

    for (Statement statement : statements) {
      boolean sameSubject = Objects.equals(statement.getSubject(), subject);
      boolean samePredicate = Objects.equals(statement.getPredicate(), predicate);
      if (sameSubject && samePredicate) {
        result.append(", ")
              .append("\n").append(StringUtils.repeat(' ', 2 * indent))
              .append(getStringValue(statement.getObject()));
      }
      else if (sameSubject) {
        if (compact) {
          result.append(";\n")
                .append(StringUtils.repeat(' ', indent))
                .append(StringUtils.rightPad(getStringValue(statement.getPredicate()), indent - 1))
                .append(" ").append(getStringValue(statement.getObject()));
        }
        else {
          result.append(";\n")
                .append(StringUtils.repeat(' ', indent))
                .append(getStringValue(statement.getPredicate()))
                .append("\n").append(StringUtils.repeat(' ', 2 * indent))
                .append(getStringValue(statement.getObject()));
        }
      }
      else {
        // end of previous subject
        if (subject != null) result.append("\n.\n\n");

        // start of new subject
        if (compact) {
          result.append(getStringValue(statement.getSubject()))
                .append("\n").append(StringUtils.repeat(' ', indent))
                .append(StringUtils.rightPad(getStringValue(statement.getPredicate()), indent - 1))
                .append(" ").append(getStringValue(statement.getObject()));
        }
        else {
          result.append(getStringValue(statement.getSubject()))
                .append("\n").append(StringUtils.repeat(' ', indent))
                .append(getStringValue(statement.getPredicate()))
                .append("\n").append(StringUtils.repeat(' ', 2 * indent))
                .append(getStringValue(statement.getObject()));
        }
      }

      subject = statement.getSubject();
      predicate = statement.getPredicate();
    }
    result.append("\n.");
  }

  @Nonnull
  private String getStringValue(RDFNode rdfNode) {
    String result = (String) rdfNode.visitWith(StringRdfVisitor.instance);
    if (!rdfNode.isURIResource()) return result;

    String shortUri = getShortUri(result);
    return shortUri != null ? shortUri : result;
  }

  @Nullable
  private String getShortUri(String result) {
    String shortResult = result.substring(1, result.length() - 1);
    for (Map.Entry<String, String> entry : namespaces.entrySet()) {
      if (!shortResult.startsWith(entry.getValue())) continue;

      String localName = StringUtils.substringAfter(shortResult, entry.getValue());
      if (localName.contains("/") || localName.contains("#")) continue;

      return entry.getKey() + ":" + localName;
    }
    return null;
  }
}
