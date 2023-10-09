package zone.cogni.asquare.cube.pagination;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
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
import java.util.function.Consumer;
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

    List<List<String>> graphUrisSublists = Lists.partition(graphUris, sublistSize);
    for (int i = 0; i < graphUrisSublists.size(); i++) {
      List<String> sublist = graphUrisSublists.get(i);
      String selectQuery = getGraphsSelectQuery(sublist);

      List<Map<String, RDFNode>> rows = select(rdfStore, selectQuery);
      rows.forEach(row -> {
        Property p = ResourceFactory.createProperty(row.get("p").asResource().getURI());
        model.add(row.get("s").asResource(), p, row.get("o"));
      });

      int currentCount = (i + 1) * sublistSize;
      log.info("(getGraphs) load {}/{} with total of {} triples",
               Math.min(currentCount, graphCount), graphCount, model.size());
    }

    return model;
  }

  /**
   * Build a select query for fetching all triples in a set of graphs.
   * We are using <code>select</code> queries because in Virtuoso construct query pagination is unreliable.
   *
   * @param graphs list of graphs from where to fetch triples
   * @return select query to fetch all triples from graphs
   */
  @Nonnull
  private String getGraphsSelectQuery(@Nonnull List<String> graphs) {
    String inPart = graphs.stream()
                          .map(uri -> "<" + uri + ">")
                          .collect(Collectors.joining(","));

    return "select ?g ?s ?p ?o " +
           "where {" +
           "  graph ?g {" +
           "    ?s ?p ?o" +
           "  }" +
           "  filter (?g in (" + inPart + ")) " +
           "}";
  }

  @Nonnull
  public Model getGraph(@Nonnull RdfStoreService rdfStore, @Nonnull String graphUri) {
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

  /**
   * Run paginated select query and handle the result row by row by the caller. No intermediate object collections are created.
   * @param rdfStore              RDF store to use when querying
   * @param query                 Select query that will be called with pagination. If the query already contains a limit then just this query will be executed.
   * @param querySolutionConsumer QuerySolution consumer, will be called on each result row.
   */
  public void select(RdfStoreService rdfStore, String query, Consumer<QuerySolution> querySolutionConsumer) {
    if (hasLimitAtEnd(query)) {  //is this needed? if somebody sends something with limit here it deserves to fail
      selectBatch(rdfStore, query, querySolutionConsumer);
      return;
    }
    int offset = 0;
    while (true) {
      String limitQuery = query + " offset " + offset + " limit " + batchSize;
      log.info("Running query offset {} and limit {}", offset, batchSize);
      int queryReturnedSize = selectBatch(rdfStore, limitQuery, querySolutionConsumer);
      log.info("Query offset {} and limit {} returned {} rows", offset, batchSize, queryReturnedSize);
      if (queryReturnedSize < batchSize) return;
      offset += batchSize;
    }
  }

  private int selectBatch(RdfStoreService rdfStore, String query, Consumer<QuerySolution> querySolutionConsumer) {
    return rdfStore.executeSelectQuery(query, resultSet -> {
      int counter = 0;
      while (resultSet.hasNext()) {
        querySolutionConsumer.accept(resultSet.next());
        counter++;
      }
      return counter;
    });
  }

  public List<Map<String, RDFNode>> select(RdfStoreService rdfStore, String query) {
    // skip if there is a limit
    if (hasLimitAtEnd(query)) {
      return rdfStore.executeSelectQuery(query, JenaQueryUtils::convertToListOfMaps);
    }

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

  private boolean hasLimitAtEnd(String query) {
    String lastPart = StringUtils.substringAfterLast(query, "}");
    return lastPart.toLowerCase().contains("limit");
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
    return convertSingleColumnToList(results, input -> input.asResource().getURI());
  }

  /**
   * Converts single column result set to a certain type using a conversion function.
   *
   * @param results    rows after query
   * @param conversion conversion function
   * @param <T>        type of elements in the list
   * @return list of results
   * @throws RuntimeException in case there is not exactly one column
   */
  public <T> List<T> convertSingleColumnToList(@Nonnull List<Map<String, RDFNode>> results,
                                               @Nonnull Function<RDFNode, T> conversion) {
    if (results.isEmpty()) return Collections.emptyList();

    Map<String, RDFNode> firstRow = results.get(0);
    if (firstRow.size() != 1)
      throw new RuntimeException("Expected exactly one result per row. Found row size of '" + firstRow.size() + "'");

    String columnName = firstRow.keySet().stream().findFirst().get();
    return results.stream()
                  .map(row -> row.get(columnName))
                  .map(conversion)
                  .collect(Collectors.toList());
  }


  public RdfStoreService getRdfStore(Model model) {
    return new InternalRdfStoreService(model);
  }
}
