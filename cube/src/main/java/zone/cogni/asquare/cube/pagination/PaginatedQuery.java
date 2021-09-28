package zone.cogni.asquare.cube.pagination;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.sem.jena.template.JenaQueryUtils;
import zone.cogni.sem.jena.template.JenaResultSetHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * smartBatching is used for getModel method
 * and handle cases where the expected model can have size greater than the 'batchSize'.
 * In this case, smartBatchingLimit is used as a limit to finish model's construction
 * and could have value << 'batchSize'.
 * eg. batchSize: 10000, smartBatchingLimit: 2000
 */
public class PaginatedQuery {

  private final long batchSize;
  private final boolean smartBatching;
  private final long smartBatchingLimit;

  public PaginatedQuery(long batchSize) {
    this(batchSize, false, 1);
  }

  public PaginatedQuery(long batchSize, boolean smartBatching, long smartBatchingLimit) {
    Preconditions.checkState(batchSize > 0, "invalid batch size " + batchSize);
    Preconditions.checkState(smartBatchingLimit > 0 && smartBatchingLimit < batchSize,
                             "invalid smart batching limit " + smartBatchingLimit);
    this.batchSize = batchSize;
    this.smartBatching = smartBatching;
    this.smartBatchingLimit = smartBatchingLimit;
  }

  @Nonnull
  public Model getGraph(RdfStoreService rdfStore, String graphUri) {
    String constructGraph = "construct { ?s ?p ?o }" +
                            " where {" +
                            "   graph <" + graphUri + "> {" +
                            "     ?s ?p ?o" +
                            "   }" +
                            " } ";

    return getModel(rdfStore, constructGraph);
  }

  @Nonnull
  public Model getModel(RdfStoreService rdfStore, String constructQuery) {
    Model model = ModelFactory.createDefaultModel();

    long batchNumber = 0;
    long triplesAdded = 0;
    while (true) {
      if (smartBatching) {
        Model part = getModel(rdfStore, constructQuery, triplesAdded);
        model.add(part);
        triplesAdded += part.size();

        if (part.size() < smartBatchingLimit) break;
      }
      else {
        Model part = getModel(rdfStore, constructQuery, batchSize * batchNumber);
        model.add(part);
        batchNumber += 1;

        if (part.size() < batchSize) break;
      }
    }

    return model;
  }

  @Nonnull
  private Model getModel(RdfStoreService rdfStore, String constructQuery, long offset) {
    String limit = " limit " + batchSize + " offset " + offset;
    String batchQuery = constructQuery + limit;

    return rdfStore.executeConstructQuery(batchQuery);
  }

  public List<Map<String, RDFNode>> select(RdfStoreService rdfStore, String query) {
    long batchNumber = 0;
    List<Map<String, RDFNode>> result = new ArrayList<>();
    while (true) {
      List<Map<String, RDFNode>> batchMap = select(rdfStore, query, batchNumber);
      result.addAll(batchMap);
      batchNumber += 1;

      if (batchMap.size() < batchSize) break;
    }

    return result;
  }

  private List<Map<String, RDFNode>> select(RdfStoreService rdfStore, String query, long batchNumber) {
    String limit = " limit " + batchSize + " offset " + (batchSize * batchNumber);
    String batchQuery = query + limit;

    return runQuery(rdfStore, batchQuery);
  }

  private List<Map<String, RDFNode>> runQuery(RdfStoreService rdfStore, String query) {
    JenaResultSetHandler<List<Map<String, RDFNode>>> convertToListOfMaps = JenaQueryUtils::convertToListOfMaps;
    return rdfStore.executeSelectQuery(query, convertToListOfMaps);
  }

  /**
   * Since result currently is somewhat inflexible, we are adding flexibility with convertors.
   */
  public List<String> convertSingleColumnUriToStringList(List<Map<String, RDFNode>> results) {
    if (results.isEmpty()) return Collections.emptyList();

    Map<String, RDFNode> firstRow = results.get(0);
    if (firstRow.size() != 1) throw new RuntimeException("Expected exactly one result per row. Found " + firstRow.size());

    return results.stream()
                  .map(row -> row.values().stream().findFirst().get())
                  .map(rdfNode -> rdfNode.asResource().getURI())
                  .collect(Collectors.toList());
  }

  public RdfStoreService getRdfStore(Model model) {
    return new InternalRdfStoreService(model);
  }
}
