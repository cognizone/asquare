package zone.cogni.asquare.cube.digest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.sem.jena.template.JenaResultSetHandlers;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class SortedBlock {

  private static final Query rootStatementsQuery = getRootStatementsQuery();

  private static Query getRootStatementsQuery() {
    String query =
            "select ?s ?p ?o {" +
            "  {" +
            "    ?s ?p ?o." +
            "    filter (isuri(?s))" +
            "  }" +
            "  union" +
            "  {" +
            "    ?s ?p ?o." +
            "    filter (isblank(?s))" +
            "    filter not exists { ?parentSubject ?parentProperty ?s }" +
            "  }" +
            "}";
    return QueryFactory.create(query);
  }

  private Statement statement;

  private List<SortedBlock> sortedBlocks;
  private String digest;

  public SortedBlock(Model model) {
    sortedBlocks = getRootBlocks(model);
    createNestedBlocks(model);
    calculateDigest();
  }

  private List<SortedBlock> getRootBlocks(Model model) {
    RdfStoreService rdfStore = new InternalRdfStoreService(model);
    List<Map<String, RDFNode>> rootStatements = getRootStatements(rdfStore);

    return rootStatements.stream()
                         .map(row -> new SortedBlock(row.get("s"),
                                                     row.get("p"),
                                                     row.get("o")))
                         .collect(Collectors.toList());
  }

  protected static List<Map<String, RDFNode>> getRootStatements(RdfStoreService rdfStore) {
    return rdfStore.executeSelectQuery(rootStatementsQuery,
                                       new QuerySolutionMap(),
                                       JenaResultSetHandlers.listOfMapsResolver);
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

  public List<SortedBlock> getSortedBlocks() {
    return sortedBlocks;
  }

  public void createNestedBlocks(Model model) {
    if (statement == null) return;
    if (!statement.getObject().isAnon()) return;

    sortedBlocks = model.listStatements(statement.getObject().asResource(), null, (RDFNode) null)
                        .toList().stream()
                        .map(SortedBlock::new)
                        .peek(block -> createNestedBlocks(model))
                        .collect(Collectors.toList());
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
