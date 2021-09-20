package zone.cogni.asquare.access.util;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.lang.CollectorStreamTriples;

public class RdfParserUtils {

  public static Literal parseLiteral(Model model, String ttlTerm) {
    RDFNode rdfNode = parseRdfNode(model, parseRdfTermFrom(ttlTerm));
    Preconditions.checkArgument(rdfNode.isLiteral(), "Parsed rdf term from serialized ttl form '" + ttlTerm + "' does not represent a Literal");
    return rdfNode.asLiteral();
  }

  public static Literal parseLiteral(String ttlTerm) {
    RDFNode rdfNode = parseRdfNode(parseRdfTermFrom(ttlTerm));
    Preconditions.checkArgument(rdfNode.isLiteral(), "Parsed rdf term from serialized ttl form '" + ttlTerm + "' does not represent a Literal");
    return rdfNode.asLiteral();
  }

  public static Resource parseResource(Model model, String ttlTerm) {
    RDFNode rdfNode = parseRdfNode(model, parseRdfTermFrom(ttlTerm));
    Preconditions.checkArgument(rdfNode.isResource(), "Parsed rdf term from serialized ttl form '" + ttlTerm + "' does not represent a Resource");
    return rdfNode.asResource();
  }

  public static RDFNode parseRdfNode(Model model, String ttlTerm) {
    return parseRdfNode(model, parseRdfTermFrom(ttlTerm));
  }

  public static RDFNode parseRdfNode(String ttlTerm) {
    return parseRdfNode(parseRdfTermFrom(ttlTerm));
  }

  private static RDFNode parseRdfNode(Model model, Node node) {
    if (node.isURI()) return model.createResource(node.getURI());
    if (node.isBlank()) return model.createResource(new AnonId(node.getBlankNodeId()));
    if (StringUtils.isNotBlank(node.getLiteralLanguage())) return model.createLiteral(node.getLiteralLexicalForm(), node.getLiteralLanguage());
    if (StringUtils.isNotBlank(node.getLiteralDatatypeURI())) return model.createTypedLiteral(node.getLiteralLexicalForm(), node.getLiteralDatatype());
    return model.createLiteral(node.getLiteralLexicalForm());
  }

  private static RDFNode parseRdfNode(Node node) {
    if (node.isURI()) return ResourceFactory.createResource(node.getURI());
    Preconditions.checkState(!node.isBlank()); // todo implement this too
    if (StringUtils.isNotBlank(node.getLiteralLanguage())) return ResourceFactory.createLangLiteral(node.getLiteralLexicalForm(), node.getLiteralLanguage());
    if (StringUtils.isNotBlank(node.getLiteralDatatypeURI())) return ResourceFactory.createTypedLiteral(node.getLiteralLexicalForm(), node.getLiteralDatatype());
    return ResourceFactory.createPlainLiteral(node.getLiteralLexicalForm());
  }

  private static Node parseRdfTermFrom(String ttlTerm) {
    CollectorStreamTriples stream = new CollectorStreamTriples();
    try {
      RDFParser.create().lang(RDFLanguages.TTL).fromString("<http://www.w3.org/2000/01/rdf-schema#Resource> <http://www.w3.org/1999/02/22-rdf-syntax-ns#value>" + ttlTerm + " .").parse(stream);
    }
    catch (Exception e) {
      Preconditions.checkArgument(false, "Failed to parse rdf term from serialized ttl form '" + ttlTerm + "': " + e.getMessage());
    }
    return stream.getCollected().stream().findFirst().get().getObject();
  }
}
