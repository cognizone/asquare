package zone.cogni.asquare.access.simplerdf;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.access.AccessType;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.access.simplerdf.filter.AttributeFilter;
import zone.cogni.asquare.access.simplerdf.filter.TypeFilter;
import zone.cogni.asquare.access.simplerdf.snippet.SnippetQueryService;
import zone.cogni.asquare.access.validation.ValidationResult;
import zone.cogni.asquare.access.validation.ValueValidation;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.Snippet;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.edit.delta.MergingDelta;
import zone.cogni.asquare.edit.delta.SparqlVisitor;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.web.rest.controller.exceptions.NotFoundException;
import zone.cogni.sem.jena.JenaUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static zone.cogni.asquare.access.simplerdf.SparqlFragment.getCoreTriple;
import static zone.cogni.asquare.rdf.TypedResourceBuilder.type;
import static zone.cogni.asquare.rdf.TypedResourceBuilder.uri;
import static zone.cogni.core.util.function.CachingSupplier.memoize;

public class SimpleRdfAccessService implements AccessService {

  private static final Logger log = LoggerFactory.getLogger(SimpleRdfAccessService.class);

  private final Map<ApplicationProfileDef, Map<ApplicationProfile.Attribute, Query>> queryCache = new IdentityHashMap<>();
  private final Map<ApplicationProfileDef, Map<ApplicationProfile.Type, Query>> typeQueryCache = new IdentityHashMap<>();

  private PrefixCcService prefixCcService;
  private Supplier<RdfStoreService> rdfStoreServiceSupplier;
  private Supplier<SnippetQueryService> snippetQueryServiceSupplier;

  public SimpleRdfAccessService() {
  }

  public SimpleRdfAccessService(PrefixCcService prefixCcService, Supplier<RdfStoreService> rdfStoreServiceSupplier) {
    Preconditions.checkNotNull(rdfStoreServiceSupplier, "rdfStoreService is null");

    this.prefixCcService = prefixCcService;
    this.rdfStoreServiceSupplier = rdfStoreServiceSupplier;
    this.snippetQueryServiceSupplier = memoize(() -> new SnippetQueryService(this));
  }

  @Override
  public AccessType getAccessType() {
    return AccessType.RDF;
  }

  public PrefixCcService getPrefixCcService() {
    return prefixCcService;
  }

  public RdfStoreService getDatabase() {
    return rdfStoreServiceSupplier.get();
  }

  public void setDatabase(RdfStoreService rdfStoreService) {
    Preconditions.checkState(rdfStoreServiceSupplier == null, "Cannot set rdfStoreServiceSupplier twice");
    rdfStoreServiceSupplier = () -> rdfStoreService;
  }

  // TODO see if this is smart
  @Override
  public RdfStoreService getRdfStoreService() {
    return rdfStoreServiceSupplier.get();
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Override
  public RdfResource getTypedResource() {
    return new RdfResource(this);
  }

  @Override
  public RdfResource getTypedResource(ApplicationProfile.Type type, Resource resource) {
    Option<RdfResource> resultOption = getTypedResourceOption(type, resource);
    return resultOption.getOrElse(() -> {
      String message = "Type " + type.getDescription() + " and resource " + resource.getURI() + " did not return results.";
      log.debug(message);
      // TODO include resource in message
      log.debug("Query used: \n{}", findOneSparqlQuery(type).get());

      throw new NotFoundException(message);
    });
  }


  /**
   * Gets filtered list of objects based on the Range rule defined on the attribute.
   * The returned Objects are the result of casting the RDF nodes to:
   * - either a Literal
   * - or a TypedResource
   */
  @Override
  public <T extends RdfValue> List<T> getValues(ApplicationProfile applicationProfile, TypedResource typedResource, ApplicationProfile.Attribute attribute) {
    Option<Snippet> snippetRule = attribute.getRule(Snippet.class);
//    Option<Snippet> snippetRule = Option.none();
    if (snippetRule.isDefined()) {
      Snippet snippet = snippetRule.get();
      return snippetQueryServiceSupplier.get()
              .getValues(applicationProfile, snippet, typedResource.getResource())
              .stream()
              .map(n -> (T) n)
              .collect(Collectors.toList());
    }

    Pair<Model, List<RDFNode>> attributeModel = getAttributeValues(typedResource, attribute);
    return attributeModel.getRight()
                         .stream()
                         .map(node -> {
                           return (T) getObject(applicationProfile, attribute, attributeModel.getLeft(), node);
                         })
                         .collect(Collectors.toList());

  }

  // todo support and implement findAll as a paged method, this method should apply a default page size and offset 0
  @Override
  public List<RdfResource> findAll(ApplicationProfile.Type type) {
    return getResources(type).stream()
                             .map(resource -> getTypedResource(type, resource))
                             .collect(Collectors.toList());
  }

  @Override
  public List<TypedResource> findAll(Supplier<ApplicationProfile.Type> typeSupplier, ApplicationView.AttributeMatcher... attributeMatchers) {
    ApplicationProfile.Type type = typeSupplier.get();
    String sparql = "SELECT ?s WHERE {" +
                    //
                    // types
                    // TODO BUG! must use TypeFilter !!!
                    type.getRules(RdfType.class).stream()
                        .map(rdfType -> "?s a <" + rdfType.getValue() + ">.")
                        .collect(Collectors.joining("\n\t\t")) +
                    "\n\t\t" +
                    //
                    // filters
                    Arrays.stream(attributeMatchers)
                          .map(filter -> {
                            ApplicationProfile.Attribute attribute = type.getAttribute(filter.getAttribute());
                            return "?s "
                                   + "<" + attribute.getUri() + "> "
                                   + filter.getRdf(attribute).visitWith(SparqlVisitor.instance()) + ".";
                          })
                          .collect(Collectors.joining("\n\t\t")) +
                    "\n\t\t" +
                    "}";

    List<Resource> resources = getRdfStoreService().executeSelectQuery(sparql, this::resultToResourceList);

    return resources.stream()
                    .map(resource -> getTypedResource(type(type), uri(resource)))
                    .collect(Collectors.toList());
  }

  private List<Resource> resultToResourceList(ResultSet result) {
    List<Resource> uris = new ArrayList<>();
    while (result.hasNext()) {
      uris.add(result.next().getResource("s"));
    }
    return uris;
  }

  @Override
  public void save(List<DeltaResource> deltaResources) {
    MergingDelta delta = new MergingDelta(deltaResources);

    String sparql = delta.getSparql();

    if (StringUtils.isNotBlank(sparql)) log.debug(sparql);

    getRdfStoreService().executeUpdateQuery(sparql);
  }

  private <T extends RdfValue> Option<T> getTypedResourceOption(ApplicationProfile.Type type, Resource resource) {
    Query query = getQuery(type);
    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add("resource", resource);

    boolean exists = rdfStoreServiceSupplier.get().executeAskQuery(query, bindings);
    if (!exists) return Option.none();

    RdfResource typedResource = getTypedResource();
    typedResource.setType(type);
    typedResource.setResource(resource);
    return (Option<T>) Option.of(typedResource);
  }

  private Pair<Model, List<RDFNode>> getAttributeValues(TypedResource typedResource, ApplicationProfile.Attribute attribute) {
    Model model = getModel(typedResource, attribute);

    // todo handle case where typedResource is a bnode!!? not sure if this helps at all???
    Resource resource = typedResource.getResource();
    resource = resource.isURIResource() ? model.createResource(resource.getURI())
                                        : model.createResource(resource.getId());

    List<RDFNode> values = model
            .listObjectsOfProperty(resource, ResourceFactory.createProperty(attribute.getUri()))
            .toList().stream()
            .filter(validateAttributeValue(attribute, model))  // during loading we only do validations to do filtering
            .collect(Collectors.toList());

    return Pair.of(model, values);
  }

  // this is the wrong way to do filtering!!
  @Deprecated
  private Predicate<RDFNode> validateAttributeValue(ApplicationProfile.Attribute attribute, Model model) {
    return value -> attribute
            .getRules().stream()
            .map(rule -> {
              ValidationResult validationResult = ValueValidation.withoutReport(model,
                                                                                value,
                                                                                attribute.getType().getApplicationProfile())
                                                                 .apply(rule);
              return new Tuple2<>(rule, validationResult);
            })
            .peek(validationTuple -> {
              if (!log.isDebugEnabled() || validationTuple._2.conforms()) return;

              log.debug("violations on attribute '{}' of class '{}':{}",
                        attribute.getAttributeId(),
                        attribute.getType().getDescription(),
                        ValueValidation.withReport(model, value, attribute.getType().getApplicationProfile()).apply(validationTuple._1));
            })
            .allMatch(validationTuple -> validationTuple._2.conforms());
  }

  private Model getModel(TypedResource typedResource, ApplicationProfile.Attribute attribute) {
    Supplier<String> sparql = () -> {
      List<SparqlFragment> sparqlFragments = new AttributeFilter().apply(attribute);
      String result = "CONSTRUCT {" + '\n' +
                      " " + getCoreTriple(attribute) + "." + '\n' +
                      " " + getConstructFragment(sparqlFragments) + '\n' +
                      "} " + '\n' +
                      "WHERE {" + '\n' +
                      " " + getCoreTriple(attribute) + "." + '\n' +
                      " " + getWhereFragment(sparqlFragments) + '\n' +
                      "}";

      if (log.isInfoEnabled()) log.info("sparql: {}", result);
      return result;
    };

    Query query = getQuery(attribute, sparql);
    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add("instance", typedResource.getResource());

    Model model = rdfStoreServiceSupplier.get().executeConstructQuery(query, bindings);
    if (log.isInfoEnabled()) log.info("model:\n{}", JenaUtils.toString(model, "turtle"));

    return model;
  }

  private Query getQuery(ApplicationProfile.Attribute attribute, Supplier<String> sparql) {
    ApplicationProfileDef rootApplicationProfile = attribute.getType().getApplicationProfile().getApplicationProfileDef().getRootDefinition();
    if (!queryCache.containsKey(rootApplicationProfile)) {
      queryCache.put(rootApplicationProfile, new HashMap<>());
    }

    Map<ApplicationProfile.Attribute, Query> queryMap = queryCache.get(rootApplicationProfile);
    if (!queryMap.containsKey(attribute)) {
      Query query = QueryFactory.create(sparql.get(), Syntax.syntaxARQ);
      queryMap.put(attribute, query);
    }

    return queryMap.get(attribute);
  }

  private Query getQuery(ApplicationProfile.Type type) {

    ApplicationProfileDef rootApplicationProfile = type.getApplicationProfile().getApplicationProfileDef().getRootDefinition();
    if (!typeQueryCache.containsKey(rootApplicationProfile)) {
      typeQueryCache.put(rootApplicationProfile, new HashMap<>());
    }

    Map<ApplicationProfile.Type, Query> queryMap = typeQueryCache.get(rootApplicationProfile);
    if (!queryMap.containsKey(type)) {
      Supplier<String> sparql = findOneSparqlQuery(type);
      Query query = QueryFactory.create(sparql.get(), Syntax.syntaxARQ);
      queryMap.put(type, query);
    }

    return queryMap.get(type);
  }

  private String getWhereFragment(List<SparqlFragment> sparqlFragments) {
    return sparqlFragments.stream()
                          .filter(sparqlFragment -> StringUtils.isNotBlank(sparqlFragment.getFilter()))
                          .map(SparqlFragment::getFilter)
                          .collect(Collectors.joining("\n "));
  }

  private String getConstructFragment(List<SparqlFragment> sparqlFragments) {
    return sparqlFragments.stream()
                          .filter(sparqlFragment -> StringUtils.isNotBlank(sparqlFragment.getConstruct()))
                          .map(SparqlFragment::getConstruct)
                          .collect(Collectors.joining("\n "));
  }

  private RdfValue getObject(ApplicationProfile applicationProfile, ApplicationProfile.Attribute attribute, Model model, RDFNode node) {
    return node.isLiteral() ? new BasicRdfValue(node.asLiteral())
                            : getSimpleOrTypedResource(applicationProfile, attribute, model, node.asResource());
  }

  private RdfValue getSimpleOrTypedResource(ApplicationProfile applicationProfile,
                                            ApplicationProfile.Attribute attribute,
                                            Model model,
                                            Resource resource) {

    List<ApplicationProfile.Type> assignableTypes = getResourceTypes(applicationProfile, attribute, model, resource);
    if (assignableTypes.isEmpty()) {
      logMissingType(model, resource);
      return new BasicRdfValue(resource);
    }

    ApplicationProfile.Type type = ApplicationProfile.Type.calculateType(assignableTypes);

    // in some cases it might match query but not the more detailed "fetching" of the resource
    return getTypedResourceOption(type, resource)
            .getOrElse(() -> new BasicRdfValue(resource));
  }

  private void logMissingType(Model model, Resource resource) {
    boolean resourceHasNoType = !model.listObjectsOfProperty(resource, RDF.type).hasNext();
    if (resourceHasNoType) return;

    List<String> typeUris = model.listObjectsOfProperty(resource, RDF.type).toList()
                                 .stream().map(rdfNode -> rdfNode.asResource().getURI())
                                 .collect(Collectors.toList());
    log.warn("Resource '{}' has types {}, but nothing is found in application profile.", resource.getURI(), typeUris);
  }

  private List<ApplicationProfile.Type> getResourceTypes(ApplicationProfile applicationProfile,
                                                         ApplicationProfile.Attribute attribute,
                                                         Model model,
                                                         Resource resource) {

    List<ApplicationProfile.Type> resourceTypes = getResourceTypes(applicationProfile, model, resource);

    // non embedded case
    Option<ApplicationProfile.Type> embeddedType = TypeFilter.getEmbeddedTypeFor(attribute);
    if (embeddedType.isEmpty()) return resourceTypes;

    // embedded case
    return resourceTypes.contains(embeddedType.get()) ? Collections.singletonList(embeddedType.get())
                                                      : Collections.emptyList();
  }

  private List<ApplicationProfile.Type> getResourceTypes(ApplicationProfile applicationProfile, Model model, Resource resource) {
    Set<String> typeResources = model.listObjectsOfProperty(resource, RDF.type).toList().stream().map(node -> node.asResource().getURI()).collect(Collectors.toSet());

    return applicationProfile.getTypes().values().stream()
                             .filter(type -> type.getRules(RdfType.class).stream()
                                                 .allMatch(rdfType -> typeResources.contains(rdfType.getValue()))).collect(Collectors.toList());
  }

  private List<Resource> getResources(ApplicationProfile.Type type) {
    String sparql = findAllSparqlQuery(type);
    return queryAndReturnResources(sparql);
  }

  private <T> T asOne(@Nonnull List<T> list) {
    Preconditions.checkState(list.size() <= 1);
    return list.isEmpty() ? null : list.get(0);
  }

  private Supplier<String> findOneSparqlQuery(ApplicationProfile.Type type) {
    return () -> "ASK " + '\n' +
                 "WHERE {" + '\n' +
                 TypeFilter.forType(type).get() +
                 "\n}";
  }

  private String findAllSparqlQuery(ApplicationProfile.Type type) {
    String sparql = "SELECT DISTINCT ?resource " + '\n' +
                    "WHERE {" + '\n' +
                    TypeFilter.forType(type).get() +
                    "}";

    if (log.isTraceEnabled()) log.trace("Query: {}\n", sparql);
    return sparql;
  }

  private List<Resource> queryAndReturnResources(String sparql) {
    return rdfStoreServiceSupplier.get().executeSelectQuery(sparql, resultSet -> {
      return Streams.stream(resultSet)
                    .map(querySolution -> querySolution.getResource("resource"))
                    .collect(Collectors.toList());
    });
  }

}
