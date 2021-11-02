package zone.cogni.asquare.cube.digest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class SortedBlock {

  private static final Query illegalGraphQuery = getIllegalGraphQuery();

  private static Query getIllegalGraphQuery() {
    String query =
            "  ask {" +
            "    ?s ?p ?o." +
            "    filter (isblank(?o))" +
            "  }" +
            "  group by ?o" +
            "  having (count(?s) > 1)";
    return QueryFactory.create(query);
  }

  private final List<SortedBlock> nestedBlocks;
  private Statement statement;
  private String digest;

  public SortedBlock(Model model) {
    if (isIllegalGraph(model)) throw new RuntimeException("blank node is used more than once");

    nestedBlocks = calculateRootBlocks(model);
    calculateDigest();
  }

  private boolean isIllegalGraph(Model model) {
    RdfStoreService rdfStore = new InternalRdfStoreService(model);
    return rdfStore.executeAskQuery(illegalGraphQuery, new QuerySolutionMap());
  }

  public SortedBlock(List<SortedBlock> nestedBlocks) {
    this.nestedBlocks = nestedBlocks;
  }

  private List<SortedBlock> calculateRootBlocks(Model model) {
    return ListUtils.union(getUriRootBlocks(model),
                           getBlankRootBlocks(model));
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
                .map(subject -> createBlocksForSubject(model, subject))
                .map(SortedBlock::new)
                .collect(Collectors.toList());
  }

  public SortedBlock(Model model, Statement statement) {
    this.statement = statement;
    nestedBlocks = calculationNestedBlocks(model);
    calculateDigest();
  }

  private List<SortedBlock> calculationNestedBlocks(Model model) {
    if (statement == null) return Collections.emptyList();
    if (!statement.getObject().isAnon()) return Collections.emptyList();

    return createBlocksForSubject(model, statement.getObject().asResource());
  }

  private List<SortedBlock> createBlocksForSubject(Model model, Resource subject) {
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
}
