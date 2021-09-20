package zone.cogni.asquare.cube.rules;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import zone.cogni.asquare.cube.spel.NamedTemplate;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SparqlRules {

  private static final Logger log = LoggerFactory.getLogger(SparqlRules.class);

  private final SpelService spelService;
  private final String ruleFolder;
  private final Map<String, List<NamedTemplate>> queryMap = new HashMap<>();

  public SparqlRules(SpelService spelService, String ruleFolder) {
    this.spelService = spelService;
    this.ruleFolder = ruleFolder;
  }

  public Model convert(Model model) {
    String root = "";
    return convert(model, root, ImmutableMap.of());
  }

  public Model convert(Model model, Map<String, String> context) {
    String root = "";
    return convert(model, root, context);
  }

  public Model convert(Model model, List<String> groups) {
    return convert(model, groups, ImmutableMap.of());
  }

  public Model convert(Model model, List<String> groups, Map<String, String> context) {
    groups.forEach(group -> convert(model, group, context));
    return model;
  }

  public Model convert(Model model, String group) {
    return convert(model, group, ImmutableMap.of());
  }

  public Model convert(Model model, String group, Map<String, String> context) {
    convert(getRdfStore(model), group, context);
    return model;
  }

  public RdfStoreService convert(InternalRdfStoreService rdfStore, String group, Map<String, String> context) {
    return convertWithPattern(rdfStore, getGroupPattern(group), context);
  }

  @Nonnull
  private String getGroupPattern(String group) {
    String selection = StringUtils.isBlank(group) ? "/*"
                                                  : "/" + group + "/*";
    return ruleFolder + selection;
  }

  public RdfStoreService convertWithPattern(InternalRdfStoreService rdfStore, String pattern, Map<String, String> context) {
    log.info("    {}", pattern);
    getQueriesUsingPattern(pattern)
      .stream()
      .sorted(Comparator.comparing(NamedTemplate::getName))
      .forEach(namedTemplate -> {
        Model modelCopy = log.isTraceEnabled() ? getModelCopy(rdfStore) : null;

        log.debug("        {}", namedTemplate.getName());
        NamedTemplate queryTemplate = spelService.processTemplate(namedTemplate, context);
        rdfStore.executeUpdateQuery(queryTemplate.getResult());

        if (log.isTraceEnabled()) logModelDifferences(modelCopy, rdfStore.getModel());
      });

    return rdfStore;
  }

  private Model getModelCopy(InternalRdfStoreService rdfStore) {
    Model result = ModelFactory.createDefaultModel();
    result.add(rdfStore.getModel());
    return result;
  }

  private void logModelDifferences(Model oldModel, Model newModel) {
    Model removedData = oldModel.difference(newModel);
    Model addedData = newModel.difference(oldModel);

    if (!removedData.isEmpty())
      log.trace("        removed  \n{}", toString(removedData));
    if (!addedData.isEmpty())
      log.trace("        added \n{}", toString(addedData));
  }

  private String toString(Model model) {
    StringWriter out = new StringWriter();
    model.write(out, "N-Triples");
    return out.toString();
  }

  @Nonnull
  private List<NamedTemplate> getQueriesUsingPattern(String groupPattern) {
    if (!queryMap.containsKey(groupPattern)) {
      Resource[] resourcesUsingPattern = getResourcesUsingPattern(groupPattern);
      queryMap.put(groupPattern, convertToNamedTemplates(resourcesUsingPattern));
    }

    return queryMap.get(groupPattern);
  }

  @Nonnull
  private List<NamedTemplate> convertToNamedTemplates(Resource[] queryResources) {
    return Arrays.stream(queryResources)
                 .map(NamedTemplate::fromResource)
                 .collect(Collectors.toList());
  }

  @Nonnull
  private Resource[] getResourcesUsingPattern(String locationPattern) {
    try {
      return new PathMatchingResourcePatternResolver().getResources(locationPattern);
    }
    catch (FileNotFoundException ignore) {
      // some patterns do not exist
      log.info("cannot find files with location pattern {}", locationPattern);
      return new Resource[]{};
    }
    catch (IOException e) {
      throw new RuntimeException("cannot access files on location pattern " + locationPattern);
    }
  }

  @Nonnull
  private InternalRdfStoreService getRdfStore(Model model) {
    return new InternalRdfStoreService(model);
  }
}
