package zone.cogni.asquare.cube.digest;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.Resource;

/**
 * Visitor to calculate string based on RDF nodes. Blank nodes are ignored.
 */
class DigestRdfVisitor implements RDFVisitor {

  protected static final DigestRdfVisitor instance = new DigestRdfVisitor();

  /**
   * @return an empty string
   */
  @Override
  public String visitBlank(Resource r, AnonId id) {
    return "";
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
      return '"' + literal.getString() + '"' + '@' + literal.getLanguage();

    if (StringUtils.isNotBlank(literal.getDatatypeURI()))
      return '"' + literal.getString() + '"' + "^^<" + literal.getDatatypeURI() + ">";

    throw new RuntimeException("how can this be possible?");
  }

}
