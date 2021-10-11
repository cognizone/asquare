package zone.cogni.asquare.cube.digest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class SortedBlock {

  /**
   * Visitor to calculate string based on RDF nodes. Blank nodes are ignored.
   */
  private static class DigestRdfVisitor implements RDFVisitor {

    private static final DigestRdfVisitor instance = new DigestRdfVisitor();

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

  private Statement statement;
  private List<SortedBlock> sortedBlocks;
  private String digest;

  public SortedBlock(List<SortedBlock> sortedBlocks) {
    this.sortedBlocks = sortedBlocks;
  }

  public SortedBlock(RDFNode subject, RDFNode predicate, RDFNode object) {
    this(
            ResourceFactory.createStatement(
                    (Resource) subject,
                    ResourceFactory.createProperty(predicate.asResource().getURI()),
                    object
            )
    );
  }

  public SortedBlock(Statement statement) {
    this.statement = statement;
  }

  public String getDigest() {
    return digest;
  }

  public void calculateDigest() {
    String digestString = "";

    // nested blocks
    if (sortedBlocks != null && !sortedBlocks.isEmpty()) {
      sortedBlocks.forEach(SortedBlock::calculateDigest);
      sortedBlocks.sort(Comparator.comparing(SortedBlock::getDigest));

      digestString = sortedBlocks.stream()
                                 .map(SortedBlock::getDigest)
                                 .collect(Collectors.joining());
    }

    // root block does not have a statement
    if (statement != null) {
      String triple = statement.getSubject().visitWith(DigestRdfVisitor.instance)
                             + " "
                             + statement.getPredicate().visitWith(DigestRdfVisitor.instance)
                             + " "
                             + statement.getObject().visitWith(DigestRdfVisitor.instance);
      digestString += triple;
    }

    this.digest = DigestUtils.sha256Hex(digestString);
  }
}
