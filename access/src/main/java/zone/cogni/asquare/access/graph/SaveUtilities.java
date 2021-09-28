package zone.cogni.asquare.access.graph;

import com.google.common.collect.Streams;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.access.ElasticAccessService;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class SaveUtilities {

  public static final int BATCH_SIZE = 10000;

  private static final Logger log = LoggerFactory.getLogger(SaveUtilities.class);

  private SaveUtilities() {
  }

  public static BiConsumer<GraphApplicationView, List<DeltaResource>> indexGraphFunction(ElasticAccessService elasticsearchAccessService,
                                                                                         List<String> types) {
    return (graphView, deltaResources) -> {
      DeltaResource mainResource = DeltaResource.fromDatabase(
        graphView.findAll(() -> graphView.getApplicationProfile().getType(types.get(0))).get(0))
                                                .getOrElseThrow(() -> new GraphViewOperationException("no main resource in this graph"));
      if (deltaResources.stream()
                        .noneMatch(deltaResource -> deltaResource.getResource()
                                                                 .getURI()
                                                                 .equals(mainResource.getResource().getURI()))) {
        deltaResources = new ArrayList<>(deltaResources);
        deltaResources.add(mainResource);
      }
      deltaResources.stream()
                    .filter(deltaResource -> types.stream()
                                                  .anyMatch(typeId -> graphView.getApplicationProfile()
                                                                               .getType(typeId)
                                                                               .equals(deltaResource.getType())))
                    .map(deltaResource -> deltaResource.isDeleted() ?
                                          getDeleteRunnable(elasticsearchAccessService, deltaResource) :
                                          getIndexRunnable(elasticsearchAccessService, deltaResource))
                    .map(CompletableFuture::runAsync)
                    .forEach(CompletableFuture::join);
    };
  }

  private static Runnable getIndexRunnable(ElasticAccessService elasticsearchAccessService, DeltaResource resourceToIndex) {
    return () -> elasticsearchAccessService.indexResource(resourceToIndex);
  }

  private static Runnable getDeleteRunnable(ElasticAccessService elasticsearchAccessService, DeltaResource resourceToDelete) {
    return () -> elasticsearchAccessService.deleteResource(resourceToDelete);
  }

  public static List<String> findResources(SparqlService sparqlService, Supplier<String> selectResourceQuery) {
    List<String> uris = new ArrayList<>();
    boolean thereAreMore = true;
    int index = 0;

    while (thereAreMore) {
      List<String> urisOfCurrentRun = sparqlService.executeSelectQuery(getResourcesQuery(selectResourceQuery, index), resultSet ->
        Streams.stream(resultSet).map(result -> result.getResource("resource").getURI()).collect(Collectors.toList()));
      uris.addAll(urisOfCurrentRun);
      if (urisOfCurrentRun.size() != BATCH_SIZE) thereAreMore = false;
      index++;
    }
    return uris;
  }

  public static String getResourcesQuery(Supplier<String> selectResourceQuery, int index) {
    return selectResourceQuery.get() + " limit " + BATCH_SIZE + " offset " + (BATCH_SIZE * index);
  }

  public static BiConsumer<GraphApplicationView, List<DeltaResource>> persistGraphFunction(SparqlService sparqlService) {
    return (view, deltaResources) -> persist(sparqlService, view.getModel(), view.getGraphUri(), true);
  }

  private static void persist(SparqlService sparqlService, Model model, String graph, boolean backupAndRestore) {
    // drop full graph
    Optional<String> backupGraph = backupAndDrop(sparqlService, graph, backupAndRestore);
    try {
      // recreate graph with updated in memory model
      sparqlService.updateGraph(graph, model);
      backupGraph.ifPresent(bckGraph -> dropQuietly(sparqlService, bckGraph));
    }
    catch (RuntimeException e) {
      log.error("failed to upload updated graph : {}", e.getMessage());
      if (backupGraph.isPresent()) {
        log.info("trying to restore graph from backup {}", backupGraph.get());
        sparqlService.executeUpdateQuery("MOVE <" + backupGraph.get() + "> TO <" + graph + ">");
        log.info("restore done.");
      }
      GraphViewOperationException.rethrow(e);
    }
  }

  private static Optional<String> backupAndDrop(SparqlService sparqlService, String graph, boolean backupAndRestore) {
    if (!backupAndRestore) {
      sparqlService.dropGraph(graph);
      return Optional.empty();
    }

    try {
      String tempGraphUri = graph + "/" + UUID.randomUUID() + "/temp";
      sparqlService.executeUpdateQuery("MOVE <" + graph + "> TO <" + tempGraphUri + ">");
      return Optional.of(tempGraphUri);
    }
    catch (Exception e) {
      log.error("ignored exception on moving graph to temp backup graph. {}", e.getMessage());
      log.info("dropping graph");
      sparqlService.dropGraph(graph);
      return Optional.empty();
    }
  }

  private static void dropQuietly(SparqlService sparqlService, String graph) {
    try {
      sparqlService.dropGraph(graph);
    }
    catch (Exception ignore) {
      log.error("ignored exception on dropping graph.", ignore);
    }
  }


}
