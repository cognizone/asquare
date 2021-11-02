package zone.cogni.asquare.cube.digest;

import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Resource;
import zone.cogni.asquare.cube.sort.StringRdfVisitor;

/**
 * Visitor to calculate string based on RDF nodes. Blank nodes are ignored.
 */
class DigestRdfVisitor extends StringRdfVisitor {

  protected static final DigestRdfVisitor instance = new DigestRdfVisitor();

  /**
   * @return an empty string
   */
  @Override
  public String visitBlank(Resource r, AnonId id) {
    return "";
  }

}
