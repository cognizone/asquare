package zone.cogni.asquare.cube.convertor.data2shacl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.Tuple2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.spring.ResourceHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                                               ImmutableMap.of("type", typeUri));
    List<Map<String, RDFNode>> rows = paginatedQuery.select(rdfStoreService, query);
    return paginatedQuery.convertSingleColumnUriToStringList(rows);
  }

  public Model generate(@Nonnull Configuration configuration,
                        @Nonnull Map<String, String> prefixes,
                        @Nonnull RdfStoreService rdfStoreService,
                        @Nonnull String graph) {
    Model model = paginatedQuery.getGraph(rdfStoreService, graph);
    return generate(configuration, prefixes, model);
  }

  public Model generate(@Nonnull Configuration configuration,
                        @Nonnull Map<String, String> prefixes,
                        @Nonnull RdfStoreService rdfStore,
                        @Nonnull List<String> graphs) {
    log.info("(generateTypeGraphs) fetched {} graph uris", graphs.size());

    Model fullModel = paginatedQuery.getGraphs(rdfStore, graphs, 10);
    return generate(configuration, prefixes, fullModel);
  }

  public Model generate(@Nonnull Configuration configuration,
                        @Nonnull Map<String, String> prefixes,
                        @Nonnull Model inputModel) {
    InternalRdfStoreService graphRdfStore = new InternalRdfStoreService(inputModel);
    return generate(configuration, prefixes, graphRdfStore);
  }

  public Model generate(@Nonnull Configuration configuration,
                        @Nonnull Map<String, String> prefixes,
                        @Nonnull RdfStoreService rdfStoreService) {
    Model shacl = ModelFactory.createDefaultModel();
    try {

      addPrefixes(prefixes, shacl);
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
            shacl.listStatements(null, RDF.type, SHACLM.NodeShape).toList().stream(),
            shacl.listStatements(null, RDF.type, SHACLM.PropertyShape).toList().stream()
    );
  }

  private void addPrefixes(@Nonnull Map<String, String> prefixes,
                           @Nonnull Model shacl) {
    shacl.setNsPrefixes(prefixes);
  }

  private void addTypes(Configuration configuration, RdfStoreService rdfStoreService, Model shacl) {
    List<String> types = getTypes(configuration, rdfStoreService);
    log.debug("(addTypes) found {} types", types.size());
    types.forEach(type -> addType(configuration, rdfStoreService, shacl, type));
  }

  private List<String> getTypes(Configuration configuration, RdfStoreService rdfStoreService) {
    List<Map<String, RDFNode>> rows = getRows(rdfStoreService, "select-types.sparql");
    List<String> types = paginatedQuery.convertSingleColumnUriToStringList(rows);

    types.sort(getIriComparator(configuration));

    return types;
  }

  private Comparator<String> getIriComparator(Configuration configuration) {
    return (t1, t2) -> {
      int priorityT1 = configuration.getPriorityIndex(getNamespaceIri(t1));
      int priorityT2 = configuration.getPriorityIndex(getNamespaceIri(t2));

      if (priorityT1 >= 0 && priorityT2 >= 0) {
        return priorityT1 == priorityT2 ? t1.compareToIgnoreCase(t2) : Integer.compare(priorityT1, priorityT2);
      }
      else if (priorityT1 >= 0 && priorityT2 == -1) {
        return -1;
      }
      else if (priorityT1 == -1 && priorityT2 >= 0) {
        return 1;
      }
      else {
        return t1.compareToIgnoreCase(t2);
      }
    };
  }

  private String getNamespaceIri(String type) {
    if (type.contains("#")) {
      return type.substring(0, type.lastIndexOf('#') + 1);
    }
    else {
      return type.substring(0, type.lastIndexOf('/') + 1);
    }
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

    shacl.add(typeShape, RDF.type, SHACLM.NodeShape);
    shacl.add(typeShape, SHACLM.targetClass, targetClass);

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
      namespacePrefix = "ns" + getAvailableNamespaceIndex(shacl);
      shacl.setNsPrefix(namespacePrefix, originalResource.getNameSpace());
    }

    String prefixLocalName = firstPart == null ? namespacePrefix + "_" + localName
                                               : firstPart + "_" + namespacePrefix + "_" + localName;
    return ResourceFactory.createResource(configuration.getShapesNamespace() + prefixLocalName);
  }

  private int getAvailableNamespaceIndex(Model shacl) {

    Optional<Integer> maxValue = shacl.getNsPrefixMap().keySet().stream()
                                      .filter(key -> key.matches("^ns[0123456789]+$"))
                                      .map(key -> Integer.parseInt(key.substring(2)))
                                      .max(Integer::compare);

    return maxValue.map(integer -> integer + 1).orElse(0);
  }

  private void addProperties(Configuration configuration,
                             RdfStoreService rdfStoreService,
                             Model shacl,
                             Resource typeShape,
                             Resource targetClass) {
    List<String> properties = getProperties(configuration, rdfStoreService, targetClass);
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

    shacl.add(typeShape, SHACLM.property, propertyShape);
    shacl.add(propertyShape, RDF.type, SHACLM.PropertyShape);

    shacl.add(propertyShape, SHACLM.path, path);

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
      shacl.add(propertyShape, SHACLM.minCount, getOneAsInteger());
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
      shacl.add(propertyShape, SHACLM.maxCount, getOneAsInteger());
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
      shacl.add(propertyShape, SHACLM.nodeKind, nodeKindValue);
    }
    else {
      log.warn("No sh:nodeKind could be derived for '{}'", propertyShape.getURI());
    }

    if (nodeKindValue == SHACLM.Literal) {
      setShaclDatatype(rdfStore, shacl, targetClass, path, propertyShape);
    }
    else if (nodeKindValue == SHACLM.IRI) {
      setShaclClass(configuration, rdfStore, shacl, targetClass, path, propertyShape);
    }
  }

  @SuppressWarnings("ConstantConditions")
  private Resource calculateNodeKind(boolean hasIri, boolean hasBlank, boolean hasLiteral) {
    if (hasIri && !hasBlank && !hasLiteral) return SHACLM.IRI;
    if (!hasIri && hasBlank && !hasLiteral) return SHACLM.BlankNode;
    if (!hasIri && !hasBlank && hasLiteral) return SHACLM.Literal;
    if (hasIri && hasBlank && !hasLiteral) return SHACLM.BlankNodeOrIRI;
    if (hasIri && !hasBlank && hasLiteral) return SHACLM.IRIOrLiteral;
    if (!hasIri && hasBlank && hasLiteral) return SHACLM.BlankNodeOrLiteral;
    return null;
  }

  private void setShaclDatatype(@Nonnull RdfStoreService rdfStore,
                                @Nonnull Model shacl,
                                @Nonnull Resource targetClass,
                                @Nonnull Resource path,
                                @Nonnull Resource propertyShape) {
    List<String> datatypes = selectUris(rdfStore, "select-datatype.sparql.spel", getTypeAndPropertyParameters(targetClass, path));
    if (datatypes.isEmpty()) {
      log.warn("type '{}' and property '{}' does not have at least one datatype",
               shortenUri(shacl, targetClass), shortenUri(shacl, path));
      return;
    }

    if (datatypes.size() > 1) {
      setOrShaclDatatype(rdfStore, shacl, targetClass, path, propertyShape, datatypes);
    }
    else {
      setSingleShaclDatatype(rdfStore, shacl, targetClass, path, propertyShape, datatypes);
    }
  }

  private void setOrShaclDatatype(RdfStoreService rdfStore,
                                  Model shacl,
                                  Resource targetClass,
                                  Resource path,
                                  Resource propertyShape,
                                  List<String> datatypes) {

    List<Tuple2<Resource, Resource>> datatypeInstances = getOrDatatypeTuples(propertyShape, datatypes);

    // add sh:or datatype instances to shacl model
    datatypeInstances.forEach(tuple -> {
      Resource orDatatypeInstance = tuple._1;
      Resource orDatatype = tuple._2;

      shacl.add(orDatatypeInstance, RDF.type, SHACLM.PropertyShape);
      shacl.add(orDatatypeInstance, SHACLM.datatype, orDatatype);

      if (orDatatype.equals(RDF.langString)) {
        setLanguageIn(rdfStore, shacl, targetClass, path, orDatatypeInstance);
      }
    });

    // add sh:or list to property shape:
    //    first create RDF list and then add it to property shape
    List<Resource> orInstances = datatypeInstances.stream()
                                                  .map(Tuple2::_1)
                                                  .collect(Collectors.toList());
    RDFList orInstancesList = shacl.createList(orInstances.iterator());
    shacl.add(propertyShape, SHACLM.or, orInstancesList);
  }

  private List<Tuple2<Resource, Resource>> getOrDatatypeTuples(@Nonnull Resource propertyShape,
                                                               @Nonnull List<String> datatypes) {
    return datatypes.stream()
                    .map(datatype -> getOrDatatypeTuple(propertyShape, datatype))
                    .collect(Collectors.toList());
  }

  private Tuple2<Resource, Resource> getOrDatatypeTuple(@Nonnull Resource propertyShape,
                                                        @Nonnull String datatype) {
    Resource datatypeValue = ResourceFactory.createResource(datatype);

    // TODO not supporting same local name in different namespaces yet
    String orInstanceName = datatypeValue.getLocalName().toLowerCase();
    Resource orInstance = ResourceFactory.createResource(propertyShape.getURI() + "/" + orInstanceName);
    return new Tuple2<>(orInstance, datatypeValue);
  }

  private void setSingleShaclDatatype(RdfStoreService rdfStore, Model shacl, Resource targetClass, Resource path, Resource propertyShape, List<String> datatypes) {
    Resource datatypeValue = ResourceFactory.createResource(datatypes.get(0));
    shacl.add(propertyShape, SHACLM.datatype, datatypeValue);


    if (RDF.langString.equals(datatypeValue)) {
      setUniqueLang(rdfStore, shacl, targetClass, path, propertyShape);
      setLanguageIn(rdfStore, shacl, targetClass, path, propertyShape);
    }
  }

  private void setUniqueLang(@Nonnull RdfStoreService rdfStore,
                             @Nonnull Model shacl,
                             @Nonnull Resource targetClass,
                             @Nonnull Resource path,
                             @Nonnull Resource propertyShape) {
    boolean isNotUniqueLang = askQuery(rdfStore,
                                       "is-not-unique-lang.sparql.spel",
                                       getTypeAndPropertyParameters(targetClass, path));
    if (isNotUniqueLang) return;

    shacl.add(propertyShape, SHACLM.uniqueLang, ResourceFactory.createTypedLiteral(true));
  }


  private void setLanguageIn(@Nonnull RdfStoreService rdfStore,
                             @Nonnull Model shacl,
                             @Nonnull Resource targetClass,
                             @Nonnull Resource path,
                             @Nonnull Resource propertyShape) {
    List<Map<String, RDFNode>> rows = getRows(rdfStore,
                                              "select-languages.sparql.spel",
                                              getTypeAndPropertyParameters(targetClass, path));

    List<Literal> languages = paginatedQuery.convertSingleColumnToList(rows, RDFNode::asLiteral);
    RDFList languagesList = shacl.createList(languages.iterator());

    shacl.add(propertyShape, SHACLM.languageIn, languagesList);
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
    shacl.add(propertyShape, SHACLM.class_, classValue);
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
    if (translation != null) return ImmutableList.of(translation);

    // cleanup unused types
    configuration.getIgnoredClasses().forEach(classSet::remove);

    // again, try to translate lots of types to 1
    String translationRetry = configuration.getTypeTranslation(classSet);
    if (translationRetry != null) return ImmutableList.of(translationRetry);

    // we tried, return as what's left
    return new ArrayList<>(classes);
  }

  private List<String> selectUris(RdfStoreService rdfStore, String fileName, Map<String, String> typeAndPropertyParameters) {
    List<Map<String, RDFNode>> rows = getRows(rdfStore, fileName, typeAndPropertyParameters);
    return paginatedQuery.convertSingleColumnUriToStringList(rows);
  }

  private Map<String, String> getTypeAndPropertyParameters(Resource type, Resource property) {
    return ImmutableMap.of("type", type.getURI(),
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
  List<String> getProperties(@Nonnull Configuration configuration,
                             @Nonnull RdfStoreService rdfStoreService,
                             @Nonnull Resource targetClass) {
    String query = spelService.processTemplate(getResource("select-properties.sparql.spel"),
                                               ImmutableMap.of("type", targetClass.getURI()));
    List<Map<String, RDFNode>> rows = paginatedQuery.select(rdfStoreService, query);
    List<String> properties = paginatedQuery.convertSingleColumnUriToStringList(rows);
    properties.sort(getIriComparator(configuration));
    return properties;
  }

  private List<Map<String, RDFNode>> getRows(RdfStoreService rdfStoreService, String fileName) {
    String selectTypesSparql = ResourceHelper.toString(getResource(fileName));
    return paginatedQuery.select(rdfStoreService, selectTypesSparql);
  }

  private org.springframework.core.io.Resource getResource(String fileName) {
    return new ClassPathResource(classpathRoot + fileName);
  }

}
