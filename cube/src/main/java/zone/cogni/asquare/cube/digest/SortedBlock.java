package zone.cogni.asquare.cube.digest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class SortedBlock {

  public static SortedBlock create(Model model) {
    List<SortedBlock> nestedBlocks = calculateRootBlocks(model);
    SortedBlock sortedBlock = new SortedBlock(nestedBlocks);

    sortedBlock.calculateDigest();
    return sortedBlock;
  }

  private static List<SortedBlock> calculateRootBlocks(Model model) {
    return ListUtils.union(getUriRootBlocks(model),
                           getBlankRootBlocks(model));
  }

  private static List<SortedBlock> getUriRootBlocks(Model model) {
    return model.listStatements(null, null, (RDFNode) null)
                .toList().stream()
                .filter(statement -> statement.getSubject().isURIResource())
                .map(statement -> new SortedBlock(model, statement))
                .collect(Collectors.toList());
  }

  private static List<SortedBlock> getBlankRootBlocks(Model model) {
    return model.listSubjects()
                .toList().stream()
                .filter(RDFNode::isAnon)
                .filter(subject -> !model.listStatements(null, null, subject).hasNext())
                .map(subject -> createBlocksForSubject(model, subject))
                .map(SortedBlock::new)
                .collect(Collectors.toList());
  }


  private final List<SortedBlock> nestedBlocks;
  private Statement statement;

  private String digest;

  @Deprecated
  public SortedBlock(Model model) {
    nestedBlocks = calculateRootBlocks(model);
    calculateDigest();
  }

  /**
   * Root block creation
   */
  private SortedBlock(Model model, Statement statement) {
    this.statement = statement;
    nestedBlocks = calculationNestedBlocks(model);
    calculateDigest();
  }

  private SortedBlock(List<SortedBlock> nestedBlocks) {
    this.nestedBlocks = nestedBlocks;
  }


  private List<SortedBlock> calculationNestedBlocks(Model model) {
    if (statement == null) return Collections.emptyList();
    if (!statement.getObject().isAnon()) return Collections.emptyList();

    return createBlocksForSubject(model, statement.getObject().asResource());
  }

  private static List<SortedBlock> createBlocksForSubject(Model model, Resource subject) {
    return model.listStatements(subject, null, (RDFNode) null)
                .toList().stream()
                .map(statement -> new SortedBlock(model, statement))
                .collect(Collectors.toList());
  }

  public void calculateDigest() {
    String digestString = "";

    // nested blocks
    if (nestedBlocks != null && !nestedBlocks.isEmpty()) {
      nestedBlocks.forEach(SortedBlock::calculateDigest);
      nestedBlocks.sort(Comparator.comparing(SortedBlock::getDigest));

      digestString = nestedBlocks.stream()
                                 .map(SortedBlock::getDigest)
                                 .collect(Collectors.joining());
    }

    // root block does not have a statement
    if (statement != null) {
      digestString += getTriple();
    }

    this.digest = DigestUtils.sha256Hex(digestString);
  }

  private String getTriple() {
    return statement.getSubject().visitWith(DigestRdfVisitor.instance)
           + " "
           + statement.getPredicate().visitWith(DigestRdfVisitor.instance)
           + " "
           + statement.getObject().visitWith(DigestRdfVisitor.instance);
  }

  public String getDigest() {
    return digest;
  }

  public Statement getStatement() {
    return statement;
  }

  @Nonnull
  public List<SortedBlock> getNestedBlocks() {
    return nestedBlocks == null ? Collections.emptyList()
                                : Collections.unmodifiableList(nestedBlocks);
  }

  public String toString() {
    return toString(0);
  }

  public String toString(int indentLevel) {
    String indent = StringUtils.repeat("    ", indentLevel);
    return indent + StringUtils.repeat("-", 120 - indent.length()) + "\n" +
           indent + "digest:    " + getDigest() + "\n" +
           indent + "statement: " + getStatement() + "\n" +
           getNestedBlocks().stream()
                            .map(block -> block.toString(indentLevel + 1))
                            .collect(Collectors.joining("\n"));
  }
}
