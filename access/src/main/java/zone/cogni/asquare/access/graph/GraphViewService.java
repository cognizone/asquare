package zone.cogni.asquare.access.graph;

import com.google.common.collect.Streams;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import zone.cogni.asquare.access.simplerdf.filter.TypeFilter;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.rdf.ResultSetMapper;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;


public class GraphViewService {

  private static final Logger log = getLogger(GraphViewService.class);

  private final SparqlService sparqlService;
  private final ApplicationProfile profile;
  private final GraphApplicationViewFactory applicationViewFactory;
  private final List<BiConsumer<GraphApplicationView, List<DeltaResource>>> codeBlocks;
  private final Optional<String> graphPattern;

  public GraphViewService(GraphApplicationViewFactory applicationViewFactory,
                          SparqlService sparqlService,
                          ApplicationProfile profile) {
    this(applicationViewFactory, sparqlService, profile, Collections.emptyList());
  }

  public GraphViewService(GraphApplicationViewFactory applicationViewFactory,
                          SparqlService sparqlService,
                          ApplicationProfile profile,
                          List<BiConsumer<GraphApplicationView, List<DeltaResource>>> codeBlocks) {
    this(applicationViewFactory, sparqlService, profile, codeBlocks, Optional.empty());
  }

  public GraphViewService(GraphApplicationViewFactory applicationViewFactory,
                          SparqlService sparqlService,
                          ApplicationProfile profile,
                          List<BiConsumer<GraphApplicationView, List<DeltaResource>>> codeBlocks,
                          Optional<String> graphPattern) {
    this.sparqlService = sparqlService;
    this.profile = profile;
    this.applicationViewFactory = applicationViewFactory;

    this.codeBlocks = new ArrayList<>(codeBlocks);
    this.graphPattern = graphPattern;
  }

  public static Model loadModel(SparqlService sparqlService, String graphUri) {
    return sparqlService.queryForModel("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                                       "Construct{ ?s ?p ?o } " +
                                       "where { " +
                                       "  graph <" + graphUri + "> { " +
                                       "    ?s ?p ?o" +
                                       "  } " +
                                       "}");
  }

  public static Model loadPaginatedModel(SparqlService sparqlService, String graphUri) {
    String whereQuery = "where { " +
                        "  graph <" + graphUri + "> { " +
                        "    ?s ?p ?o" +
                        "  } " +
                        "}";
    String countQuery = "select (count(?s) as ?amount) " + whereQuery;
    String constructQuery = "Construct{ ?s ?p ?o } " + whereQuery;

    Integer amount = sparqlService.executeSelectQuery(countQuery, ResultSetMapper::resultSetToAmount);
    if (amount < SaveUtilities.BATCH_SIZE) {
      return sparqlService.queryForModel(constructQuery);
    }
    Model resultModel = ModelFactory.createDefaultModel();

    int nPages = amount % SaveUtilities.BATCH_SIZE != 0
                 ? (amount / SaveUtilities.BATCH_SIZE + 1)
                 : (amount / SaveUtilities.BATCH_SIZE);

    int nStatements = 0;
    for (int i = 0; i < nPages; i++) {
      String query = SaveUtilities.getResourcesQuery(() -> constructQuery, i);
      Model model = sparqlService.queryForModel(query);
      resultModel.add(model);
      nStatements += model.listStatements().toList().size();
    }

    if (nStatements != amount) {
      throw new GraphViewOperationException("Amount of statements is not equal to amount of count(?x) {} != {}", nStatements, amount);
    }

    return resultModel;
  }

  public ApplicationProfile getProfile() {
    return profile;
  }

  public Optional<String> getGraphPattern() {
    return graphPattern;
  }

  public GraphApplicationView get(String graphUri) {
    Model model = loadModel(graphUri);

    return applicationViewFactory.createGraphApplicationView(profile, model, graphUri, codeBlocks);
  }

  /**
   * This method is using paginated load model
   */
  public GraphApplicationView getPaginated(String graphUri) {
    Model model = loadPaginatedModel(sparqlService, graphUri);

    return applicationViewFactory.createGraphApplicationView(profile, model, graphUri, codeBlocks);
  }

  public GraphApplicationView multi(String graphUri, UnaryOperator<String> getGraphQueryByResourceUri) {
    Model model = loadModel(graphUri);

    return applicationViewFactory.createMultiGraphApplicationView(profile, model, graphUri, codeBlocks, getGraphQueryByResourceUri, this);
  }

  public Optional<GraphApplicationView> getGraphApplicationView(String instanceUri,
                                                                UnaryOperator<String> getGraphQueryByInstanceUri) {
    String graphUri = findGraphUri(instanceUri, getGraphQueryByInstanceUri);
    return graphUri == null ? Optional.empty() : Optional.of(get(graphUri));
  }

  public Optional<GraphApplicationView> getPaginatedGraphApplicationView(String instanceUri,
                                                                UnaryOperator<String> getGraphQueryByInstanceUri) {
    String graphUri = findGraphUri(instanceUri, getGraphQueryByInstanceUri);
    return graphUri == null ? Optional.empty() : Optional.of(getPaginated(graphUri));
  }

  public Optional<GraphApplicationView> getGraphApplicationView(String instanceUri) {
    return getGraphApplicationView(instanceUri, this::getGraphQueryByInstanceUri);
  }

  public Optional<GraphApplicationView> getPaginatedGraphApplicationView(String instanceUri) {
    return getPaginatedGraphApplicationView(instanceUri, this::getGraphQueryByInstanceUri);
  }

  public List<String> findAll(String typeId) {
    return findAll(profile.getType(typeId));
  }

  private Model loadModel(String graphUri) {
    return loadModel(sparqlService, graphUri);
  }

  public String findGraphUri(String instanceUri) {
    return findGraphUri(instanceUri, this::getGraphQueryByInstanceUri);
  }

  public String findGraphUri(String instanceUri, UnaryOperator<String> getGraphQueryByInstanceUri) {
    String selectGraphQuery = getGraphQueryByInstanceUri.apply(instanceUri);

    Set<String> graphs = sparqlService.executeSelectQuery(
      selectGraphQuery,
      resultSet -> Streams.stream(resultSet)
                          .map(querySolution -> querySolution.getResource("?graph"))
                          .map(Resource::getURI)
                          .filter(graphUri -> !StringUtils.endsWithIgnoreCase(graphUri, "/temp"))
                          .collect(Collectors.toSet())
    );

    String graph = graphs.isEmpty() ? null
                                    : graphs.iterator().next();
    if (graphs.size() > 1) {
      log.error("Uri {} has multiple graphs: {}\n\t-> picking the first one: {}",
                instanceUri, graphs, graph);
    }

    return graph;
  }

  public SparqlService getSparqlService() {
    return sparqlService;
  }

  private String getGraphQueryByInstanceUri(String instanceUri) {
    return "PREFIX rdf: <" + RDF.uri + ">" +
           "SELECT DISTINCT ?graph { " +
           "  GRAPH ?graph { " +
           "    <" + instanceUri + "> ?p ?o." +
           "    " + graphPattern.orElse("") +
           "    FILTER ( ?p != rdf:type ) " +
           "  } " +
           "}";
  }

  private List<String> findAll(ApplicationProfile.Type type) {
    String select = "SELECT DISTINCT ?resource " +
                    "WHERE { " +
                    "  GRAPH ?g { " +
                    " " + TypeFilter.forType(type).get() +
                    " " + graphPattern.orElse("") +
                    "  } " +
                    "}";
    return SaveUtilities.findResources(sparqlService, () -> select);
  }

}
