package zone.cogni.asquare.cube.pagination;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import joptsimple.internal.Strings;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class PaginatedQuery {

  private static final Logger log = LoggerFactory.getLogger(PaginatedQuery.class);

  private final long batchSize;

  public PaginatedQuery(long batchSize) {
    Preconditions.checkState(batchSize > 0, "invalid batch size " + batchSize);
    this.batchSize = batchSize;
  }

  /**
   * <p>
   * Loads all triples from a list of graphs in a Jena model.
   * </p>
   * <p>
   * Note: implementation is mostly to increase performance.
   * You need to tweak the sublist size to get optimal performance.
   * </p>
   * <p>
   * Note 2: implementation uses <code>select</code> since Virtuoso pagination has issues when using
   * <code>construct</code> queries.
   * </p>
   *
   * @param rdfStore    RDF store to use when querying
   * @param graphUris   list of graph uris to load in to model
   * @param sublistSize number of graphs to load in one go
   *                    Be careful to not load too many at the same time since that will make the query slow.
   *                    Also note that loading multiple huge graphs using this method will make loading data slower,
   *                    think 10000+ triples per graph, in case where number number of triples is below a few 100s
   *                    performance will be great.
   * @return Jena model containing all triples of listed graphs
   */
  @Nonnull
  public Model getGraphs(RdfStoreService rdfStore, List<String> graphUris, int sublistSize) {
    int graphCount = graphUris.size();
    Model model = ModelFactory.createDefaultModel();

    List<List<String>> lists = Lists.partition(graphUris, sublistSize);
    for (int i = 0; i < lists.size(); i++) {
      List<String> graphs = lists.get(i);
      String selectQuery = getGraphsSelectQuery(graphs);

      List<Map<String, RDFNode>> rows = select(rdfStore, selectQuery);
      rows.forEach(row -> {
        Property p = ResourceFactory.createProperty(row.get("p").asResource().getURI());
        model.add((Resource) row.get("s"), p, row.get("o"));
      });

      int currentCount = (i + 1) * sublistSize;
      log.info("(getGraphs) load {}/{} with total of {} triples",
               Math.min(currentCount, graphCount), graphCount, model.size());
    }

    return model;
  }

  private String getGraphsSelectQuery(List<String> graphs) {
    String inPart = Strings.join(graphs.stream().map(s -> "<" + s + ">").collect(Collectors.toList()),
                                 ", ");
    String selectQuery = "select ?g ?s ?p ?o " +
                         "where {" +
                         "  graph ?g {" +
                         "    ?s ?p ?o" +
                         "  }" +
                         "  filter (?g in (" + inPart + ")) " +
                         "}";

    return selectQuery;
  }

  @Nonnull
  public Model getGraph(RdfStoreService rdfStore, String graphUri) {
    String constructGraph = "construct { ?s ?p ?o }" +
                            " where {" +
                            "   graph <" + graphUri + "> {" +
                            "     ?s ?p ?o" +
                            "   }" +
                            " } ";

    return getModelFromQuery(rdfStore, constructGraph);
  }

  private Model getModelFromQuery(RdfStoreService rdfStore, String constructGraph) {
    Model model = ModelFactory.createDefaultModel();
    long batchNumber = 0;
    while (true) {
      Model part = getModel(rdfStore, constructGraph, batchSize * batchNumber);
      model.add(part);
      batchNumber += 1;

      if (part.size() < batchSize) break;
    }

    return model;
  }

  @Nonnull
  public Model getModel(RdfStoreService rdfStore, String constructQuery) {
    Model model = ModelFactory.createDefaultModel();

    long batchNumber = 0;
    while (true) {
      Model part = getModel(rdfStore, constructQuery, batchSize * batchNumber);
      model.add(part);
      batchNumber += 1;

      if (part.isEmpty()) break;
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
    if (firstRow.size() != 1)
      throw new RuntimeException("Expected exactly one result per row. Found " + firstRow.size());

    return results.stream()
                  .map(row -> row.values().stream().findFirst().get())
                  .map(rdfNode -> rdfNode.asResource().getURI())
                  .collect(Collectors.toList());
  }

  public RdfStoreService getRdfStore(Model model) {
    return new InternalRdfStoreService(model);
  }
}
