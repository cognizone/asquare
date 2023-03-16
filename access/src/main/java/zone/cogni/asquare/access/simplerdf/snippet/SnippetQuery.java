package zone.cogni.asquare.access.simplerdf.snippet;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import org.apache.commons.lang3.RegExUtils;
import org.apache.jena.rdf.model.RDFNode;
import zone.cogni.asquare.applicationprofile.rules.Snippet;
import zone.cogni.asquare.edit.delta.SparqlVisitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SnippetQuery implements Supplier<String> {

  private final Map<String, RDFNode> parameters = new HashMap<>();
  private final Snippet snippet;

  public SnippetQuery(Snippet snippet) {
    this.snippet = snippet;
  }


  public SnippetQuery withInputParameters(Object... parameters) {
    if (parameters.length == 1) {
      Object untypedValue = parameters[0];
      Preconditions.checkState(untypedValue instanceof RDFNode,
                               "Parameter must be of type RDFNode. Found '" + untypedValue + "' of type " + untypedValue.getClass().getName());

      String parameterName = snippet.getInputParameters().stream().findFirst().get();
      this.parameters.put(parameterName, (RDFNode) untypedValue);
      return this;
    }

    Preconditions.checkState(parameters.length % 2 == 0, "Need an even number of parameters.");

    for (int i = 0; i < parameters.length / 2; i++) {
      Object untypedName = parameters[i * 2];
      Object untypedValue = parameters[i * 2 + 1];
      Preconditions.checkState(untypedName instanceof String,
                               "Parameter must be of type String. Found '" + untypedName + "' of type " + untypedName.getClass().getName());
      Preconditions.checkState(untypedValue instanceof RDFNode,
                               "Parameter must be of type RDFNode. Found '" + untypedValue + "' of type " + untypedValue.getClass().getName());

      String name = (String) untypedName;
      RDFNode value = (RDFNode) untypedValue;

      this.parameters.put(name, value);
    }

    return this;
  }


  public String get() {
    Preconditions.checkState(snippet.getInputParameters().size() == parameters.size(),
                             "Input parameters of snippets must all be filled?!");

    // TODO finish variable replacement!! => make it a bit smarter
    return "select " + getSelectPart() + " {\n"
           + getBindingsPart()
           + "  " + getQueryPart() + "\n"
           + getTypePart()
           + "}";
  }

  private String getQueryPart() {
    final String[] snippetWrapper = {this.snippet.getValue()};

    parameters.keySet()
              .forEach(key -> {
                snippetWrapper[0] = RegExUtils.replaceAll(snippetWrapper[0], "\\$" + key, "?" + key);
              });

    return snippetWrapper[0];
  }

  private String getTypePart() {
    return snippet.getOutputParameters().stream()
            .map(out -> "\tOPTIONAL { ?" + out + " a ?" + out + "Type }.\n")
            .collect(Collectors.joining());
  }

  public Set<String> getPrefixes() {
    return snippet.getNamespacePrefixes();
  }

  private StringBuilder getBindingsPart() {
    StringBuilder bindings = new StringBuilder();

    parameters.forEach((key, value) -> appendBinding(bindings, key, value));
    return bindings;
  }

  private void appendBinding(StringBuilder bindings, String key, RDFNode value) {
    bindings.append("  BIND ( ")
            .append(value.visitWith(SparqlVisitor.instance()))
            .append(" as ?")
            .append(key)
            .append(" )\n");
  }

  private String getSelectPart() {
    List<String> select = snippet.getOutputParameters().stream().map(s -> "?" + s).collect(Collectors.toList());
    select.addAll(snippet.getOutputParameters().stream().map(s -> "?" + s + "Type").collect(Collectors.toList()));
    return String.join(" ", select);
  }
}
