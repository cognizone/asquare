package zone.cogni.asquare.cube.convertor.data2shacl;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.access.shacl.Shacl;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.spring.ResourceHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShaclGenerator {

  private static final Logger log = LoggerFactory.getLogger(ShaclGenerator.class);

  private static final String classpathRoot = "convertor/data2shacl/";

  private final SpelService spelService;
  private final PaginatedQuery paginatedQuery;

  public ShaclGenerator(SpelService spelService, PaginatedQuery paginatedQuery) {
    this.spelService = spelService;
    this.paginatedQuery = paginatedQuery;
  }

  public Model generateTypeGraphs(@Nonnull Configuration configuration,
                                  @Nonnull Map<String, String> prefixes,
                                  @Nonnull RdfStoreService rdfStore,
                                  @Nonnull String typeUri) {
    log.info("(generateTypeGraphs) start");

    List<String> graphsOfType = getGraphsOfType(rdfStore, typeUri);
    return generate(configuration, prefixes, rdfStore, graphsOfType);
  }

  private List<String> getGraphsOfType(RdfStoreService rdfStoreService, String typeUri) {
    String query = spelService.processTemplate(getResource("type-graphs/select-type-graphs.sparql.spel"),
                                               Map.of("type", typeUri));
    List<Map<String, RDFNode>> rows = paginatedQuery.select(rdfStoreService, query);
    return paginatedQuery.convertSingleColumnUriToStringList(rows);
  }

  public Model generate(@Nonnull Configuration configuration,
                        @Nonnull Map<String, String> prefixes,
                        @Nonnull RdfStoreService rdfStoreService,
                        @Nonnull String graph) {
    Model model = paginatedQuery.getGraph(rdfStoreService, graph);
    InternalRdfStoreService graphRdfStore = new InternalRdfStoreService(model);
    return generate(configuration, prefixes, graphRdfStore);
  }

  public Model generate(@Nonnull Configuration configuration,
                        @Nonnull Map<String, String> prefixes,
                        @Nonnull RdfStoreService rdfStore,
                        @Nonnull List<String> graphs) {
    log.info("(generateTypeGraphs) fetched {} graph uris", graphs.size());

    Model fullModel = paginatedQuery.getGraphs(rdfStore, graphs, 10);
    InternalRdfStoreService graphRdfStore = new InternalRdfStoreService(fullModel);

    return generate(configuration, prefixes, graphRdfStore);
  }

  public Model generate(@Nonnull Configuration configuration,
                        @Nonnull Map<String, String> prefixes,
                        @Nonnull RdfStoreService rdfStoreService) {
    Model shacl = ModelFactory.createDefaultModel();
    try {

      addPrefixes(configuration, prefixes, shacl);
      log.debug("(generate) add prefixes done: {}", prefixes.size());

      addTypes(configuration, rdfStoreService, shacl);
      log.debug("(generate) add types done");

      return shacl;
    }
    catch (RuntimeException e) {
      shacl.write(System.out, "ttl");
      throw e;
    }
  }

  private Stream<Statement> getShapes(@Nonnull Model shacl) {
    return Stream.concat(
            shacl.listStatements(null, RDF.type, Shacl.NodeShape).toList().stream(),
            shacl.listStatements(null, RDF.type, Shacl.PropertyShape).toList().stream()
    );
  }

  private void addPrefixes(@Nonnull Configuration configuration,
                           @Nonnull Map<String, String> prefixes,
                           @Nonnull Model shacl) {
    doPrefixesCheck(configuration, prefixes);

    shacl.setNsPrefixes(prefixes);

    if (configuration.isIncludeEmptyShapesNamespace()) {
      shacl.setNsPrefix(configuration.getShapesPrefix(), configuration.getShapesNamespace());
    }
  }

  private void doPrefixesCheck(Configuration configuration, Map<String, String> prefixes) {
    boolean samePrefix = prefixes.containsKey(configuration.getShapesPrefix());
    if (!samePrefix) return;

    String message = "configuration shape namespace with prefix '" + configuration.getShapesPrefix() + "'"
                     + " and with uri '" + configuration.getShapesNamespace() + "'";
    boolean sameNamespace = prefixes.get(configuration.getShapesPrefix()).equals(configuration.getShapesNamespace());
    if (sameNamespace) {
      throw new RuntimeException(
              message
              + " already defined in prefixes;"
              + " it is highly recommended to create a new and unique shacl namespace!"
      );
    }

    // same prefix different namespace
    throw new RuntimeException(
            message
            + " has same prefix as one of prefixes. overlapping prefix namespace" +
            " '" + prefixes.get(configuration.getShapesPrefix()) + "'."
    );
  }

  private void addTypes(Configuration configuration, RdfStoreService rdfStoreService, Model shacl) {
    List<String> types = getTypes(rdfStoreService);
    log.debug("(addTypes) found {} types", types.size());
    types.forEach(type -> addType(configuration, rdfStoreService, shacl, type));
  }

  private List<String> getTypes(RdfStoreService rdfStoreService) {
    List<Map<String, RDFNode>> rows = getRows(rdfStoreService, "select-types.sparql");
    return paginatedQuery.convertSingleColumnUriToStringList(rows);
  }

  private void addType(@Nonnull Configuration configuration,
                       @Nonnull RdfStoreService rdfStoreService,
                       @Nonnull Model shacl,
                       @Nonnull String typeUri) {
    if (isIgnoredType(configuration, typeUri)) {
      log.info(getMessage("ignoring type '{}'", shortenUri(shacl, typeUri)));
      return;
    }

    Resource targetClass = ResourceFactory.createResource(typeUri);
    Resource typeShape = calculateShapeBasedOnResource(configuration, shacl, null, targetClass);

    shacl.add(typeShape, RDF.type, Shacl.NodeShape);
    shacl.add(typeShape, Shacl.targetClass, targetClass);

    if (log.isDebugEnabled())
      log.debug("(addType) shape name '{}' for targetClass '{}'", typeShape.getURI(), targetClass.getURI());

    addProperties(configuration, rdfStoreService, shacl, typeShape, targetClass);
  }

  private boolean isIgnoredType(Configuration configuration, String typeUri) {
    return CollectionUtils.isNotEmpty(configuration.getIgnoredClasses())
           && configuration.getIgnoredClasses().contains(typeUri);
  }

  private Resource calculateShapeBasedOnResource(@Nonnull Configuration configuration,
                                                 @Nonnull Model shacl,
                                                 @Nullable String firstPart,
                                                 @Nonnull Resource originalResource) {
    String localName = firstPart == null ? originalResource.getLocalName()
                                         : firstPart + "/" + originalResource.getLocalName();
    Resource typeShape = ResourceFactory.createResource(configuration.getShapesNamespace() + localName);

    boolean hasSameTypeShape = shacl.contains(typeShape, null, (RDFNode) null);
    if (!hasSameTypeShape) return typeShape;

    String namespacePrefix = shacl.getNsURIPrefix(originalResource.getNameSpace());
    if (namespacePrefix == null) {
      throw new RuntimeException("no name alternative found for '" + originalResource.getURI() + "':"
                                 + " please add namespace to prefixes.");
    }

    String prefixLocalName = firstPart == null ? namespacePrefix + "_" + localName
                                               : firstPart + "_" + namespacePrefix + "_" + localName;
    return ResourceFactory.createResource(configuration.getShapesNamespace() + prefixLocalName);
  }

  private void addProperties(Configuration configuration,
                             RdfStoreService rdfStoreService,
                             Model shacl,
                             Resource typeShape,
                             Resource targetClass) {
    List<String> properties = getProperties(rdfStoreService, targetClass);
    if (log.isDebugEnabled())
      log.debug("(addProperties) shape '{}' has {} properties", typeShape.getLocalName(), properties.size());

    properties.forEach(property -> addProperty(configuration,
                                               rdfStoreService,
                                               shacl,
                                               typeShape,
                                               targetClass,
                                               property));
  }

  private void addProperty(@Nonnull Configuration configuration,
                           @Nonnull RdfStoreService rdfStoreService,
                           @Nonnull Model shacl,
                           @Nonnull Resource typeShape,
                           @Nonnull Resource targetClass,
                           @Nonnull String property) {

    Resource path = ResourceFactory.createResource(property);
    Resource propertyShape = calculateShapeBasedOnResource(configuration, shacl, targetClass.getLocalName(), path);

    if (log.isDebugEnabled())
      log.debug("(addProperty) shape '{}' gets '{}'", typeShape.getLocalName(), propertyShape.getLocalName());

    shacl.add(typeShape, Shacl.property, propertyShape);
    shacl.add(propertyShape, RDF.type, Shacl.PropertyShape);

    shacl.add(propertyShape, Shacl.path, path);

    setMinCount(rdfStoreService, shacl, targetClass, path, propertyShape);
    setMaxCount(rdfStoreService, shacl, targetClass, path, propertyShape);

    setNodeKind(configuration, rdfStoreService, shacl, targetClass, path, propertyShape);
  }

  private void setMinCount(@Nonnull RdfStoreService rdfStore,
                           @Nonnull Model shacl,
                           @Nonnull Resource targetClass,
                           @Nonnull Resource path,
                           @Nonnull Resource propertyShape) {
    if (log.isTraceEnabled()) log.trace("(setMinCount) start");

    boolean hasInstanceWithoutProperty = askQuery(rdfStore, "has-instance-without-property.sparql.spel", getTypeAndPropertyParameters(targetClass, path));
    if (!hasInstanceWithoutProperty) {
      shacl.add(propertyShape, Shacl.minCount, getOneAsInteger());
    }
  }

  private void setMaxCount(@Nonnull RdfStoreService rdfStore,
                           @Nonnull Model shacl,
                           @Nonnull Resource targetClass,
                           @Nonnull Resource path,
                           @Nonnull Resource propertyShape) {
    if (log.isTraceEnabled()) log.trace("(setMaxCount) start");

    boolean hasInstanceWithTwoProperties = askQuery(rdfStore, "has-instance-with-two-properties.sparql.spel", getTypeAndPropertyParameters(targetClass, path));
    if (!hasInstanceWithTwoProperties) {
      shacl.add(propertyShape, Shacl.maxCount, getOneAsInteger());
    }
  }

  private Literal getOneAsInteger() {
    return ResourceFactory.createTypedLiteral("1", XSDDatatype.XSDinteger);
  }

  private void setNodeKind(@Nonnull Configuration configuration,
                           @Nonnull RdfStoreService rdfStore,
                           @Nonnull Model shacl,
                           @Nonnull Resource targetClass,
                           @Nonnull Resource path,
                           @Nonnull Resource propertyShape) {
    if (log.isTraceEnabled()) log.trace("(setNodeKind) start");

    Map<String, String> typeAndPropertyParameters = getTypeAndPropertyParameters(targetClass, path);
    boolean hasIri = askQuery(rdfStore, "nodekind-is-iri.sparql.spel", typeAndPropertyParameters);
    boolean hasBlank = askQuery(rdfStore, "nodekind-is-blank.sparql.spel", typeAndPropertyParameters);
    boolean hasLiteral = askQuery(rdfStore, "nodekind-is-literal.sparql.spel", typeAndPropertyParameters);

    Resource nodeKindValue = calculateNodeKind(hasIri, hasBlank, hasLiteral);
    if (nodeKindValue != null) {
      shacl.add(propertyShape, Shacl.nodeKind, nodeKindValue);
    }
    else {
      log.warn("No sh:nodeKind could be derived for '{}'", propertyShape.getURI());
    }

    if (nodeKindValue == Shacl.NodeKind.Literal) {
      setShaclDatatype(rdfStore, shacl, targetClass, path, propertyShape);
    }
    else if (nodeKindValue == Shacl.NodeKind.IRI) {
      setShaclClass(configuration, rdfStore, shacl, targetClass, path, propertyShape);
    }
  }

  @SuppressWarnings("ConstantConditions")
  private Resource calculateNodeKind(boolean hasIri, boolean hasBlank, boolean hasLiteral) {
    if (hasIri && !hasBlank && !hasLiteral) return Shacl.NodeKind.IRI;
    if (!hasIri && hasBlank && !hasLiteral) return Shacl.NodeKind.BlankNode;
    if (!hasIri && !hasBlank && hasLiteral) return Shacl.NodeKind.Literal;
    if (hasIri && hasBlank && !hasLiteral) return Shacl.NodeKind.BlankNodeOrIRI;
    if (hasIri && !hasBlank && hasLiteral) return Shacl.NodeKind.IRIOrLiteral;
    if (!hasIri && hasBlank && hasLiteral) return Shacl.NodeKind.BlankNodeOrLiteral;
    return null;
  }

  private void setShaclDatatype(@Nonnull RdfStoreService rdfStore,
                                @Nonnull Model shacl,
                                @Nonnull Resource targetClass,
                                @Nonnull Resource path,
                                @Nonnull Resource propertyShape) {
    List<String> datatypes = selectUris(rdfStore, "select-datatype.sparql.spel", getTypeAndPropertyParameters(targetClass, path));
    if (datatypes.size() != 1) {
      log.warn(getMessage("type '{}' and property '{}' does not have exactly one datatype: {}",
                          shortenUri(shacl, targetClass), shortenUri(shacl, path), shortenUri(shacl, datatypes)));
      return;
    }

    Resource datatypeValue = ResourceFactory.createResource(datatypes.get(0));
    shacl.add(propertyShape, Shacl.datatype, datatypeValue);

    if (RDF.langString.equals(datatypeValue)) {
      setLanguageIn(rdfStore, shacl, targetClass, path, propertyShape);
    }
  }

  private void setLanguageIn(@Nonnull RdfStoreService rdfStore,
                             @Nonnull Model shacl,
                             @Nonnull Resource targetClass,
                             @Nonnull Resource path,
                             @Nonnull Resource propertyShape) {

    List<Map<String, RDFNode>> rows = getRows(rdfStore,
                                              "select-languages.sparql.spel",
                                              getTypeAndPropertyParameters(targetClass, path));
    List<String> languages = paginatedQuery.convertSingleColumnToList(rows,
                                                                      input -> input.asLiteral().getString());
    languages.forEach(language -> {
      shacl.add(propertyShape, Shacl.languageIn, language);
    });
  }

  @Nonnull
  private List<String> shortenUri(@Nonnull Model shacl, @Nonnull List<String> uris) {
    return uris.stream()
               .map(uri -> shortenUri(shacl, uri))
               .collect(Collectors.toList());
  }

  @Nonnull
  private String shortenUri(@Nonnull Model shacl, @Nonnull String uri) {
    return shortenUri(shacl, ResourceFactory.createResource(uri));
  }

  @Nonnull
  private String shortenUri(@Nonnull Model shacl, @Nonnull Resource resource) {
    String prefix = shacl.getNsURIPrefix(resource.getNameSpace());
    if (prefix == null) return resource.getURI();

    return prefix + ":" + resource.getLocalName();
  }

  private String getMessage(String messagePattern, Object... parameters) {
    return MessageFormatter.arrayFormat(messagePattern, parameters)
                           .getMessage();
  }

  private void setShaclClass(Configuration configuration,
                             RdfStoreService rdfStoreService,
                             Model shacl,
                             Resource targetClass,
                             Resource path,
                             Resource propertyShape) {
    List<String> classes = calculateClasses(configuration, rdfStoreService, targetClass, path);

    if (classes.isEmpty()) {
      String message = getMessage("type '{}' and property '{}' is considered an 'rdfs:Resource'.",
                                  shortenUri(shacl, targetClass), shortenUri(shacl, path));
      log.warn(message);
      return;
    }


    if (classes.size() != 1) {
      String message = getMessage("type '{}' and property '{}' does not have exactly one class: {}",
                                  shortenUri(shacl, targetClass), shortenUri(shacl, path), shortenUri(shacl, classes));
      log.warn(message);
      return;
    }

    Resource classValue = ResourceFactory.createResource(classes.get(0));
    shacl.add(propertyShape, Shacl.classP, classValue);
  }

  private List<String> calculateClasses(@Nonnull Configuration configuration,
                                        @Nonnull RdfStoreService rdfStore,
                                        @Nonnull Resource targetClass,
                                        @Nonnull Resource path) {
    List<String> classes = selectUris(rdfStore, "select-class.sparql.spel", getTypeAndPropertyParameters(targetClass, path));
    // return is 0 or 1 result
    if (classes.size() <= 1) return classes;

    // try to translate lots of types to 1
    Set<String> classSet = new HashSet<>(classes);
    String translation = configuration.getTypeTranslation(classSet);
    if (translation != null) return List.of(translation);

    // cleanup unused types
    classSet.removeAll(configuration.getIgnoredClasses());

    // again, try to translate lots of types to 1
    String translationRetry = configuration.getTypeTranslation(classSet);
    if (translationRetry != null) return List.of(translationRetry);

    // we tried, return as what's left
    return new ArrayList<>(classes);
  }

  private List<String> selectUris(RdfStoreService rdfStore, String fileName, Map<String, String> typeAndPropertyParameters) {
    List<Map<String, RDFNode>> rows = getRows(rdfStore, fileName, typeAndPropertyParameters);
    return paginatedQuery.convertSingleColumnUriToStringList(rows);
  }

  private Map<String, String> getTypeAndPropertyParameters(Resource type, Resource property) {
    return Map.of("type", type.getURI(),
                  "property", property.getURI());
  }

  private List<Map<String, RDFNode>> getRows(@Nonnull RdfStoreService rdfStore,
                                             @Nonnull String fileName,
                                             @Nonnull Map<String, String> parameters) {
    String query = spelService.processTemplate(getResource(fileName), parameters);
    return paginatedQuery.select(rdfStore, query);
  }

  private boolean askQuery(RdfStoreService rdfStoreService, String queryFile, Map<String, String> parameters) {
    String query = spelService.processTemplate(getResource(queryFile), parameters);
    return rdfStoreService.executeAskQuery(query);
  }

  private @Nonnull
  List<String> getProperties(@Nonnull RdfStoreService rdfStoreService,
                             @Nonnull Resource targetClass) {
    String query = spelService.processTemplate(getResource("select-properties.sparql.spel"),
                                               Map.of("type", targetClass.getURI()));
    List<Map<String, RDFNode>> rows = paginatedQuery.select(rdfStoreService, query);
    return paginatedQuery.convertSingleColumnUriToStringList(rows);
  }

  private List<Map<String, RDFNode>> getRows(RdfStoreService rdfStoreService, String fileName) {
    String selectTypesSparql = ResourceHelper.toString(getResource(fileName));
    return paginatedQuery.select(rdfStoreService, selectTypesSparql);
  }

  private org.springframework.core.io.Resource getResource(String fileName) {
    return new ClassPathResource(classpathRoot + fileName);
  }

}
