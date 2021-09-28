package zone.cogni.asquare.cube.sparql2json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PropertyConversion implements Function<Model, JsonNode> {

  public static PropertyConversion fromName(String name) {
    String key = name;
    boolean asList = key.endsWith("List");
    key = strip(name, "List");

    Function<RDFNode, JsonNode> conversion = asString();
    if (key.endsWith("Boolean")) {
      conversion = asBoolean();
      key = strip(key, "Boolean");
    }
    else if (key.endsWith("Integer")) {
      conversion = asInteger();
      key = strip(key, "Integer");
    }
    else if (key.endsWith("String")) {
      conversion = asString();
      key = strip(key, "String");
    }

    key = stripEndLineChars(key);

    return new PropertyConversion(name, key, conversion, asList);
  }

  private static String strip(String name, String ending) {
    String result = name.endsWith(ending) ? StringUtils.substringBeforeLast(name, ending)
                                          : name;
    return stripEndLineChars(result);
  }

  private static String stripEndLineChars(String name) {
    String result = name;
    while (result.charAt(result.length() - 1) == '-' || result.charAt(result.length() - 1) == '_') {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  public static Function<RDFNode, JsonNode> asBoolean() {
    return new Function<RDFNode, JsonNode>() {
      @Override
      public JsonNode apply(RDFNode rdfNode) {
        if (!rdfNode.isLiteral()) throw new RuntimeException("Node " + rdfNode.toString() + " is not a literal.");

        Literal literal = rdfNode.asLiteral();

        if (XSDDatatype.XSDboolean.getURI().equals(literal.getDatatypeURI())) {
          return JsonNodeFactory.instance.booleanNode(literal.getBoolean());
        }
        else if (XSDDatatype.XSDinteger.getURI().equals(literal.getDatatypeURI())) {
          return JsonNodeFactory.instance.booleanNode(literal.getInt() == 1);
        }

        throw new RuntimeException("Node " + rdfNode.toString() + " is of correct type.");
      }

      public String toString() {
        return "asBoolean";
      }
    };

  }

  public static Function<RDFNode, JsonNode> asInteger() {
    return new Function<RDFNode, JsonNode>() {
      @Override
      public JsonNode apply(RDFNode rdfNode) {
        if (!rdfNode.isLiteral()) throw new RuntimeException("Node " + rdfNode.toString() + " is not a literal.");

        long number = rdfNode.asLiteral().getLong();
        return JsonNodeFactory.instance.numberNode(number);
      }

      public String toString() {
        return "asInteger";
      }
    };
  }

  public static Function<RDFNode, JsonNode> asString() {
    return new Function<RDFNode, JsonNode>() {
      @Override
      public JsonNode apply(RDFNode rdfNode) {
        if (rdfNode == null) return null;
        if (rdfNode.isAnon()) throw new RuntimeException("Node " + rdfNode.toString() + " is a blank node.");

        if (rdfNode.isURIResource()) {
          String uri = rdfNode.asResource().getURI();
          return uri.equals("null") ? null : JsonNodeFactory.instance.textNode(uri);
        }

        Literal literal = rdfNode.asLiteral();
        return JsonNodeFactory.instance.textNode(literal.getString());
      }

      public String toString() {
        return "asString";
      }
    };
  }

  private final String propertyName;
  private final String key;
  private final Function<RDFNode, JsonNode> conversion;
  private final boolean asList;

  public PropertyConversion(String propertyName, String key, Function<RDFNode, JsonNode> conversion, boolean asList) {
    this.propertyName = propertyName;
    this.key = key;
    this.conversion = conversion;
    this.asList = asList;
  }

  public String getKey() {
    return key;
  }

  @Override
  public JsonNode apply(Model model) {
    List<JsonNode> nodes = convert(model);

    if (asList) {
      if (nodes.isEmpty()) return null;

      ArrayNode result = JsonNodeFactory.instance.arrayNode();
      nodes.forEach(result::add);

      return result;
    }

    if (nodes.size() > 1) throw new RuntimeException("Bigger collections are not supported (yet??)");
    return nodes.isEmpty() ? null : nodes.get(0);
  }

  public String getPropertyName() {
    return propertyName;
  }

  public boolean isList() {
    return asList;
  }

  public Function<RDFNode, JsonNode> getConversion() {
    return conversion;
  }

  @Nonnull
  private List<JsonNode> convert(Model model) {
    return model.listStatements().toList().stream()
        .filter(statement -> statement.getPredicate().getLocalName().equals(propertyName))
        .map(Statement::getObject)
        .map(conversion)
        .collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return "PropertyConversion{" +
           "propertyName='" + propertyName + '\'' +
           ", conversion=" + conversion +
           ", asList=" + asList +
           '}';
  }
}
