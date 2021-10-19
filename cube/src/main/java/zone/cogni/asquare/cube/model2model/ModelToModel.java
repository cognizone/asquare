package zone.cogni.asquare.cube.model2model;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.spel.NamedTemplate;
import zone.cogni.asquare.cube.spel.TemplateService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.spring.ResourceHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Takes
 * <ul>
 *   <li>an input model</li>
 *   <li>a list of construct queries</li>
 * </ul>
 *
 * and returns a new model based on the output of the construct queries.
 */
public class ModelToModel {

  private static final Logger log = LoggerFactory.getLogger(ModelToModel.class);

  private final TemplateService templateService;
  private final Resource[] queryResources;

  public ModelToModel(TemplateService templateService, Resource[] queryResources) {
    this.templateService = templateService;
    this.queryResources = queryResources;
  }

  public Model convert(Model model, String group) {
    return convert(model, group, Collections.emptyMap());
  }

  public Model convert(Model model, String group, Map<String, String> context) {
    RdfStoreService rdfStore = getRdfStore(model);

    Model result = ModelFactory.createDefaultModel();
    getQueries(group).forEach(namedTemplate -> {
      String sparql = templateService.processTemplate(namedTemplate.getTemplate(), context);
      Model queryModel = rdfStore.executeConstructQuery(sparql);

      if (log.isDebugEnabled())
        log.debug("{} - query model size: {}", namedTemplate.getName(), queryModel.size());

      result.add(queryModel);
    });

    return result;
  }

  private List<NamedTemplate> getQueries(String group) {
    return Arrays.stream(queryResources)
                 .filter(resource -> resourceBelongsToGroup(resource, group))
                 .map(NamedTemplate::fromResource)
                 .collect(Collectors.toList());
  }

  private boolean resourceBelongsToGroup(Resource resource, String group) {
    String path = ResourceHelper.getUrl(resource).toString();
    List<String> split = Arrays.asList(StringUtils.split(path, "/"));
    String pathGroup = split.get(split.size() - 2);
    return StringUtils.equals(pathGroup, group);
  }

  private RdfStoreService getRdfStore(Model model) {
    return new InternalRdfStoreService(model);
  }

}
