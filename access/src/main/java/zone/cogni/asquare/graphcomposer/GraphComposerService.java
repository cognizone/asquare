package zone.cogni.asquare.graphcomposer;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import zone.cogni.asquare.access.graph.GraphApplicationView;
import zone.cogni.asquare.access.graph.GraphViewService;
import zone.cogni.asquare.graphcomposer.model.GraphComposerModel;
import zone.cogni.asquare.graphcomposer.model.GraphComposerSubject;
import zone.cogni.asquare.rdf.TypedResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GraphComposerService {

  private static final Logger log = LoggerFactory.getLogger(GraphComposerService.class);

  private final GraphViewService graphViewService;
  private final GraphComposerProcessor graphComposerProcessor;

  public GraphComposerService(GraphViewService graphViewService,
                              GraphComposerProcessor graphComposerProcessor) {
    this.graphComposerProcessor = graphComposerProcessor;
    this.graphViewService = graphViewService;
  }

  public Map<String, List<GraphComposerSubject>> groupSubjectsByGraph(GraphComposerModel model, Map<String, String> context) {
    return model.getSubjects()
                .stream()
                .filter(subject -> {
                  String exists = subject.getExists(context);
                  return !StringUtils.equals(exists, "false");
                })
                .collect(Collectors.toMap(subject -> subject.getGraph(context), subject -> {
                                            List<GraphComposerSubject> list = new ArrayList<>();
                                            list.add(subject);
                                            return list;
                                          },
                                          (oldValue, newValue) -> {
                                            oldValue.add(newValue.get(0));
                                            return oldValue;
                                          }
                ));
  }

  protected Map<String, Set<String>> processSubjects(Map<String, List<GraphComposerSubject>> graphsSubjects,
                                                     Map<String, String> context) {
    final Map<String, Set<String>> graphByEntityUri = new HashMap<>();
    for (String graphUri : graphsSubjects.keySet()) {
      log.info("GraphComposer has started creating subjects for graph {}", graphUri);

      GraphApplicationView view = graphViewService.get(graphUri);
      List<String> graphUris = graphComposerProcessor.createSubjectsInGraph(view, graphsSubjects.get(graphUri), context);
      graphUris.forEach(entityUri -> graphByEntityUri.computeIfAbsent(entityUri, map -> new HashSet<>()).add(graphUri));

      log.info("GraphComposer has finished creating subjects for graph {}", graphUri);
    }
    return graphByEntityUri;
  }

  private String findGraphForUri(String uri,
                                 String defaultGraphUri,
                                 Map<String, Set<String>> graphByEntityUri,
                                 Set<String> defaultGraphByEntityUri) {
    if (defaultGraphByEntityUri.contains(uri)) {
      return defaultGraphUri;
    }
    if (graphByEntityUri.containsKey(uri)) {
      Set<String> graphs = graphByEntityUri.get(uri);
      if (CollectionUtils.isEmpty(graphs)) {
        return defaultGraphUri;
      }
      return CollectionUtils.lastElement(graphs);
    }
    return defaultGraphUri;
  }

  protected Map<String, Map<String, TypedResource>> processAttributes(Map<String, List<GraphComposerSubject>> graphsSubjects,
                                                                      Map<String, Set<String>> graphByEntityUri,
                                                                      Map<String, String> context,
                                                                      Model inputModel) {
    Map<String, Map<String, TypedResource>> resourcesByGraph = new HashMap<>();
    for (String graphUri : graphsSubjects.keySet()) {
      resourcesByGraph.computeIfAbsent(graphUri, m -> new HashMap<>());

      List<GraphComposerSubject> subjects = graphsSubjects.get(graphUri);
      log.info("GraphComposer has started creating attributes for subjects in graph {}", graphUri);

      Set<String> defaultGraphByEntityUri = subjects.stream().map(GraphComposerSubject::getUri).collect(Collectors.toSet());
      GraphApplicationView view = graphViewService.multi(graphUri, uri -> findGraphForUri(uri, graphUri, graphByEntityUri, defaultGraphByEntityUri));
      resourcesByGraph.get(graphUri).putAll(graphComposerProcessor.assignAttributesToGraphEntities(view, subjects, context, inputModel));
      log.info("GraphComposer has finished creating attributes for subjects in graph {}", graphUri);
    }
    return resourcesByGraph;
  }

  public Map<String, Map<String, TypedResource>> make(GraphComposerModel graphComposerModel, Map<String, String> context) {
    return make(graphComposerModel, context, null, null);
  }

  public Map<String, Map<String, TypedResource>> make(GraphComposerModel graphComposerModel, Map<String, String> context, Model rdf, String defaultGraph) {
    Map<String, List<GraphComposerSubject>> graphsSubjects = groupSubjectsByGraph(graphComposerModel, context);

    if (log.isInfoEnabled()) {
      String graphsList = String.join(", ", graphsSubjects.keySet());
      log.info("GraphComposer has found following graphs {} in model {}", graphsList, graphComposerModel);
    }
    Map<String, Set<String>> graphByEntityUri = processSubjects(graphsSubjects, context);
    Map<String, Map<String, TypedResource>> graphsSubjectsWithAttributes = processAttributes(graphsSubjects, graphByEntityUri, context, rdf);

    if (rdf != null && StringUtils.isNotBlank(defaultGraph)) {

      Map<String, List<Statement>> statementsByGraph = new HashMap<>();

      // collect all urls that should be matched as regex
      List<String> regexUrlList = graphByEntityUri.keySet().stream()
                                                  .filter(key -> StringUtils.startsWith(key, "~"))
                                                  .collect(Collectors.toList());

      rdf.listStatements().toList().forEach(s -> {
        String entityUrl = s.getSubject().getURI();

        Set<String> graphs = new HashSet<>();

        // assign regex urls to corresponding graphs
        regexUrlList.stream()
                    .filter(regexUrl -> Pattern.compile(StringUtils.removeStart(regexUrl, "~")).matcher(entityUrl).matches())
                    .forEach(regexUrl -> {
                      graphByEntityUri.get(regexUrl).forEach(graph -> {
                        graphs.add(graph);
                      });
                    });

        // assign urls to corresponding graphs
        if (graphByEntityUri.containsKey(entityUrl)) {
          graphByEntityUri.get(entityUrl).forEach(graph -> {
            graphs.add(graph);
          });
        }

        // when no graph assigned go for default graph
        if (graphs.size() == 0) {
          graphs.add(defaultGraph);
        }

        graphs.forEach(graph -> statementsByGraph.computeIfAbsent(graph, key -> new ArrayList<>()).add(s));
      });

      for (String graph : statementsByGraph.keySet()) {
        List<Statement> statements = statementsByGraph.get(graph);
        Model model = ModelFactory.createDefaultModel().add(statements);
        if (model.size() > 0) {
          try {
            graphViewService.getSparqlService().updateGraph(graph, model);
            log.info("Graph {} uploaded {} rdf statements: {}", graph, statements.size(), model);
          }
          catch (Exception ex) {
            log.error("Graph {} failed uploading {} rdf statements: {}", graph, statements.size(), model, ex);
          }
        }
        else {
          log.info("Graph {} has no statements to upload", graph);
        }
      }
    }

    return graphsSubjectsWithAttributes;
  }

}
