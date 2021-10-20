package zone.cogni.asquare.cube.digest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class SortedBlock {

  private Statement statement;

  private List<SortedBlock> nestedBlocks;
  private String digest;

  public SortedBlock(Model model) {
    nestedBlocks = calculateRootBlocks(model);

//    nestedBlocks = getRootBlocks(model);
//    createNestedBlocks(model);
    calculateDigest();
  }

  public SortedBlock(List<SortedBlock> nestedBlocks) {
    this.nestedBlocks = nestedBlocks;
  }

  private List<SortedBlock> calculateRootBlocks(Model model) {
    return ListUtils.union(getUriRootBlocks(model),
                           getBlankRootBlocks(model));
//    return model.listSubjects()
//                .toList().stream()
//                .filter(subject -> isRoot(model, subject))
//                .flatMap(subject -> model.listStatements(subject, null, (RDFNode) null).toList().stream())
//                .map(currentStatement -> new SortedBlock(model, currentStatement))
//                .collect(Collectors.toList());
  }

  private List<SortedBlock> getUriRootBlocks(Model model) {
    return model.listStatements(null, null, (RDFNode) null)
                .toList().stream()
                .filter(statement -> statement.getSubject().isURIResource())
                .map(statement -> new SortedBlock(model, statement))
                .collect(Collectors.toList());
  }

  private List<SortedBlock> getBlankRootBlocks(Model model) {
    return model.listSubjects()
                .toList().stream()
                .filter(RDFNode::isAnon)
                .filter(subject -> !model.listStatements(null, null, subject).hasNext())
                .map(subject -> convertStatementsToBlocks(model,
                                                          model.listStatements(subject, null, (RDFNode) null)))
                .map(SortedBlock::new)
                .collect(Collectors.toList());
  }

//  private boolean isRoot(Model model, Resource subject) {
//    if (subject.isURIResource()) return true;
//
//     must not have usages!
//    return !model.listStatements(null, null, subject).hasNext();
//  }

  public SortedBlock(Model model, Statement statement) {
    this.statement = statement;
    nestedBlocks = calculationNestedBlocks(model);
    calculateDigest();
  }

  private List<SortedBlock> calculationNestedBlocks(Model model) {
    if (statement == null) return Collections.emptyList();
    if (!statement.getObject().isAnon()) return Collections.emptyList();

    StmtIterator stmtIterator = model.listStatements(statement.getObject().asResource(), null, (RDFNode) null);
    return convertStatementsToBlocks(model, stmtIterator);

  }

  private List<SortedBlock> convertStatementsToBlocks(Model model, StmtIterator iterator) {
    return iterator
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
      String triple = statement.getSubject().visitWith(DigestRdfVisitor.instance)
                      + " "
                      + statement.getPredicate().visitWith(DigestRdfVisitor.instance)
                      + " "
                      + statement.getObject().visitWith(DigestRdfVisitor.instance);
      digestString += triple;
    }

    this.digest = DigestUtils.sha256Hex(digestString);
  }

  public String getDigest() {
    return digest;
  }

  public Statement getStatement() {
    return statement;
  }

  public List<SortedBlock> getNestedBlocks() {
    return nestedBlocks == null ? Collections.emptyList()
                                : Collections.unmodifiableList(nestedBlocks);
  }
}
