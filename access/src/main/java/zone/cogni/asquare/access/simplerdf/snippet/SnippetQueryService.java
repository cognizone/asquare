package zone.cogni.asquare.access.simplerdf.snippet;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.access.simplerdf.RdfResource;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.Snippet;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.sem.jena.template.JenaResultSetHandlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SnippetQueryService {

  private static final Logger log = LoggerFactory.getLogger(SnippetQueryService.class);

  private final SimpleRdfAccessService rdfAccessService;

  public SnippetQueryService(SimpleRdfAccessService rdfAccessService) {
    this.rdfAccessService = rdfAccessService;
  }

  public List<RdfValue> getValues(ApplicationProfile applicationProfile, Snippet snippet, Object... parameters) {
    Preconditions.checkState(snippet.getOutputParameters().size() == 1, "Expected 1 output.");
    String parameterName = snippet.getOutputParameters().stream().findFirst().get();

    List<RdfValue> results = new ArrayList<>();
    Map<String, Set<String>> typedResults = new HashMap<>();
    run(snippet, parameters)
            .forEach(row -> {
              RDFNode rdfNode = row.get(parameterName);
              RDFNode typeNode = row.getOrDefault(parameterName + "Type", null);
              if (typeNode == null) {
                results.add(rdfNode.isLiteral() ? new BasicRdfValue(rdfNode.asLiteral())
                                                : new BasicRdfValue(rdfNode.asResource()));
              }
              else {
                typedResults
                        .computeIfAbsent(rdfNode.asResource().getURI(), newSet -> new HashSet<>())
                        .add(typeNode.asResource().getURI());
              }
            });

    results.addAll(typedResults.entrySet().stream()
                                  .map(entry -> {
                                    RdfResource typedResource = rdfAccessService.getTypedResource();
                                    typedResource.setResource(ResourceFactory.createResource(entry.getKey()));
                                    typedResource.setType(filterType(applicationProfile, entry.getValue()));
                                    return typedResource;
                                  }).collect(Collectors.toSet()));
    return results;
  }

  private ApplicationProfile.Type filterType(ApplicationProfile applicationProfile, Set<String> typeUris) {
    Collection<ApplicationProfile.Type> allTypes = applicationProfile.getTypes().values();
    Set<String> knownTypeUris = typeUris.stream()
            .filter(typeUri -> allTypes.stream()
                    .anyMatch(type -> type.getRules(RdfType.class).stream()
                            .anyMatch(rule -> rule.getValue().equals(typeUri))))
            .collect(Collectors.toSet());

    return allTypes.stream()
            .filter(type -> {
              List<RdfType> typeRules = type.getRules(RdfType.class);
              return typeRules.size() == knownTypeUris.size() && typeRules.stream()
                      .allMatch(typeRule -> knownTypeUris.contains(typeRule.getValue()));
            })
            .findFirst()
            .get();
  }

  public List<Map<String, RDFNode>> run(Snippet snippet, Object... parameters) {
    return run(new SnippetQuery(snippet).withInputParameters(parameters));
  }

  public List<Map<String, RDFNode>> run(SnippetQuery snippetQuery) {
    String query = "\n" + getPrefixPart(snippetQuery) + "\n" + snippetQuery.get();
    log.info(query);

    return rdfAccessService.getRdfStoreService()
                           .executeSelectQuery(query, JenaResultSetHandlers.listOfMapsResolver);
  }

  private String getPrefixPart(SnippetQuery snippetQuery) {
    return snippetQuery.getPrefixes().stream()
                       .map(this::getPrefixLine)
                       .collect(Collectors.joining());
  }

  private String getPrefixLine(String prefix) {
    String prefixPart = StringUtils.rightPad(prefix + ":", 8);
    String namespacePart = rdfAccessService.getPrefixCcService().getNamespace(prefix);

    return "PREFIX " + prefixPart + " <" + namespacePart + ">\n";
  }
}
