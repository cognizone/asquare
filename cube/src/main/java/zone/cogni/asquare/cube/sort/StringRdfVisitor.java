package zone.cogni.asquare.cube.sort;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.XSD;

/**
 * Visitor to calculate string based on RDF nodes.
 * Returns correctly formatted RDF for resources and literals.
 */
public class StringRdfVisitor implements RDFVisitor {

  protected static final StringRdfVisitor instance = new StringRdfVisitor();

  /**
   * @return blank node using _:id format with blank node label as id
   */
  @Override
  public String visitBlank(Resource r, AnonId id) {
    return "_:" + id.getLabelString();
  }

  /**
   * @return uri surrounded by < and >
   */
  @Override
  public String visitURI(Resource r, String uri) {
    return "<" + uri + ">";
  }

  /**
   * @return literal formatted according to the RDF standards
   */
  @Override
  public String visitLiteral(Literal literal) {
    if (StringUtils.isNotBlank(literal.getLanguage()))
      return getQuotedLiteralString(literal.getString()) + '@' + literal.getLanguage();

    if (StringUtils.isNotBlank(literal.getDatatypeURI())) {
      boolean isString = literal.getDatatypeURI().equals(XSD.xstring.getURI());
      if (isString) return getQuotedLiteralString(literal.getString());

      return getQuotedLiteralString(literal.getString()) + "^^<" + literal.getDatatypeURI() + ">";
    }

    throw new RuntimeException("how can this be possible? literal " + literal);
  }

  @Override
  public String visitStmt(Resource r, Statement statement) {
    String result = "";

    Resource subject = statement.getSubject();
    Property predicate = statement.getPredicate();
    RDFNode object = statement.getObject();

    // visit subject
    result += subject.visitWith(StringRdfVisitor.instance) + " ";

    // visit predicate
    result += predicate.visitWith(StringRdfVisitor.instance) + " ";

    // visit object
    result += object.visitWith(StringRdfVisitor.instance) + ".";

    return result;
  }

  private String getQuotedLiteralString(String literalString) {
    boolean isMultiline = literalString.contains("\n");
    boolean containsDoubleQuote = literalString.contains("\"");
    if (!isMultiline && !containsDoubleQuote)
      return '"' + literalString + '"';

    boolean containsSingleQuote = literalString.contains("'");
    if (!isMultiline && !containsSingleQuote)
      return "'" + literalString + "'";

    boolean containsTripleSingleQuote = literalString.contains("'''");
    if (!containsTripleSingleQuote)
      return "'''" + literalString + "'''";

    boolean containsTripleDoubleQuote = literalString.contains("\"\"\"");
    if (!containsTripleDoubleQuote)
      return "\"\"\"" + literalString + "\"\"\"";

    throw new RuntimeException("weird literal found: " + literalString);
  }

}
