package zone.cogni.asquare.cube.convertor.data2shacl;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.access.shacl.Shacl;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.core.spring.ResourceHelper;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    log.info("(generateTypeGraphs) fetched {} graph uris", graphsOfType.size());

    Model fullModel = ModelFactory.createDefaultModel();
    for (int i = 0; i < graphsOfType.size(); i++) {
      String graph = graphsOfType.get(i);

      Model model = paginatedQuery.getGraph(rdfStore, graph);
      fullModel.add(model);


      int graphCount = i + 1;
      if (graphCount % 20 == 0) {
        log.info("(generateTypeGraphs) load {}/{} with total of {} triples", graphCount, graphsOfType.size(), fullModel.size());
      }
      else {
        log.debug("(generateTypeGraphs) load {}/{} with total of {} triples", graphCount, graphsOfType.size(), fullModel.size());
      }
    }

    log.info("(generateTypeGraphs) loaded {} triples", fullModel.size());

    InternalRdfStoreService graphRdfStore = new InternalRdfStoreService(fullModel);
    return generate(configuration, prefixes, graphRdfStore);
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
                        @Nonnull RdfStoreService rdfStoreService) {
    Model shacl = ModelFactory.createDefaultModel();
    try {

      addPrefixes(configuration, prefixes, shacl);
      addTypes(configuration, rdfStoreService, shacl);

      if (configuration.isIncludeShapesGraph()) {
        addShapesGraph(configuration, shacl);
      }

      return shacl;
    }
    catch (RuntimeException e) {
      shacl.write(System.out, "ttl");
      throw e;
    }
  }

  private void addShapesGraph(@Nonnull Configuration configuration,
                              @Nonnull Model shacl) {
    Resource shapesGraph = ResourceFactory.createResource(configuration.getShapesNamespace() + "ShapesGraph");
    shacl.add(shapesGraph, RDF.type, Shacz.ShapesGraph);

    getShapes(shacl)
            .map(Statement::getSubject)
            .forEach(shape -> shacl.add(shapesGraph, Shacz.shapes, shape));
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
    // todo better validation for overlap
    // todo make shapes prefix not overlap data
    shacl.setNsPrefixes(prefixes);
    shacl.setNsPrefix(configuration.getShapesPrefix(), configuration.getShapesNamespace());
  }

  private void addTypes(Configuration configuration, RdfStoreService rdfStoreService, Model shacl) {
    List<String> types = getTypes(rdfStoreService);
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
    if (CollectionUtils.isNotEmpty(configuration.getIgnoredClasses())
        && configuration.getIgnoredClasses().contains(typeUri)) {
      log.info("Ignoring type '{}'", typeUri);
      return;
    }
    Resource targetClass = ResourceFactory.createResource(typeUri);
    String localName = targetClass.getLocalName();

    // todo same type but different namespace might still end up in same shape
    Resource typeShape = ResourceFactory.createResource(configuration.getShapesNamespace() + localName);
    if (shacl.contains(typeShape, null, (RDFNode) null)) {
      log.error("type shape {} already exists", typeShape.getURI());
      return;
    }

    shacl.add(typeShape, RDF.type, Shacl.NodeShape);
    shacl.add(typeShape, Shacl.targetClass, targetClass);

    addProperties(configuration, rdfStoreService, shacl, typeShape, targetClass);
  }

  private void addProperties(Configuration configuration,
                             RdfStoreService rdfStoreService,
                             Model shacl,
                             Resource typeShape,
                             Resource targetClass) {
    List<String> properties = getProperties(rdfStoreService, targetClass);
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

    // todo same property but different namespace might still end up in same shape
    Resource propertyShape = ResourceFactory.createResource(configuration.getShapesNamespace() + path.getLocalName());
    if (shacl.contains(propertyShape, null, (RDFNode) null)) {
      log.error("property shape {} already exists", propertyShape.getURI());
      return;
    }

    shacl.add(typeShape, Shacl.property, propertyShape);
    shacl.add(propertyShape, RDF.type, Shacl.PropertyShape);
    shacl.add(propertyShape, Shacl.path, path);

    setMinCount(configuration, rdfStoreService, shacl, targetClass, path, propertyShape);
    setMaxCount(configuration, rdfStoreService, shacl, targetClass, path, propertyShape);

    setNodeKind(configuration, rdfStoreService, shacl, targetClass, path, propertyShape);

  }

  private void setMinCount(Configuration configuration, RdfStoreService rdfStoreService, Model shacl, Resource targetClass, Resource path, Resource propertyShape) {
    boolean hasInstanceWithoutProperty = askTypeProperty(rdfStoreService,
                                                         "has-instance-without-property.sparql.spel",
                                                         targetClass,
                                                         path);
    if (!hasInstanceWithoutProperty) {
      shacl.add(propertyShape, Shacl.minCount, ResourceFactory.createTypedLiteral(1));
    }
  }

  private void setMaxCount(Configuration configuration,
                           RdfStoreService rdfStoreService,
                           Model shacl,
                           Resource targetClass,
                           Resource path,
                           Resource propertyShape) {
    boolean hasInstanceWithTwoProperties = askTypeProperty(rdfStoreService,
                                                           "has-instance-with-two-properties.sparql.spel",
                                                           targetClass,
                                                           path);
    if (!hasInstanceWithTwoProperties) {
      shacl.add(propertyShape, Shacl.maxCount, ResourceFactory.createTypedLiteral(1));
    }
  }

  private void setNodeKind(Configuration configuration,
                           RdfStoreService rdfStoreService,
                           Model shacl,
                           Resource targetClass,
                           Resource path,
                           Resource propertyShape) {
    boolean hasIri = askTypeProperty(rdfStoreService,
                                     "nodekind-is-iri.sparql.spel",
                                     targetClass,
                                     path);

    boolean hasBlank = askTypeProperty(rdfStoreService,
                                       "nodekind-is-blank.sparql.spel",
                                       targetClass,
                                       path);
    boolean hasLiteral = askTypeProperty(rdfStoreService,
                                         "nodekind-is-literal.sparql.spel",
                                         targetClass,
                                         path);

    Resource nodeKindValue = calculateNodeKind(hasIri, hasBlank, hasLiteral);
    if (nodeKindValue != null) {
      shacl.add(propertyShape, Shacl.nodeKind, nodeKindValue);
    }

    if (nodeKindValue == Shacl.NodeKind.Literal) {
      setShaclDatatype(configuration, rdfStoreService, shacl, targetClass, path, propertyShape);
    }
    else if (nodeKindValue == Shacl.NodeKind.IRI) {
      setShaclClass(configuration, rdfStoreService, shacl, targetClass, path, propertyShape);
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

  private void setShaclDatatype(Configuration configuration,
                                RdfStoreService rdfStoreService,
                                Model shacl,
                                Resource targetClass,
                                Resource path,
                                Resource propertyShape) {
    List<String> datatypes = selectForTypeAndProperty(rdfStoreService, "select-datatype.sparql.spel",
                                                      targetClass, path);
    if (datatypes.size() != 1) {
      log.warn("type '{}' and property '{}' does not have exactly one datatype: {}", targetClass, path, datatypes);
      return;
    }

    Resource datatypeValue = ResourceFactory.createResource(datatypes.get(0));
    shacl.add(propertyShape, Shacl.datatype, datatypeValue);
  }

  private void setShaclClass(Configuration configuration,
                             RdfStoreService rdfStoreService,
                             Model shacl,
                             Resource targetClass,
                             Resource path,
                             Resource propertyShape) {
    List<String> classes = selectForTypeAndProperty(rdfStoreService, "select-class.sparql.spel",
                                                    targetClass, path);
    if (classes.isEmpty()) {
      log.warn("type '{}' and property '{}' is empty.", targetClass, path);
      return;
    }

    if (classes.size() > 1 && CollectionUtils.isNotEmpty(configuration.getIgnoredClasses())) {
      classes = new ArrayList<>(CollectionUtils.removeAll(classes, configuration.getIgnoredClasses()));
    }

    if (classes.size() != 1) {
      log.warn("type '{}' and property '{}' does not have exactly one class: {}", targetClass, path, classes);
      return;
    }

    Resource classValue = ResourceFactory.createResource(classes.get(0));
    shacl.add(propertyShape, Shacl.classP, classValue);
  }

  private List<String> selectForTypeAndProperty(RdfStoreService rdfStoreService,
                                                String fileName,
                                                Resource type,
                                                Resource property) {
    Map<String, String> parameters = Map.of("type", type.getURI(),
                                            "property", property.getURI());
    String query = spelService.processTemplate(getResource(fileName),
                                               parameters);
    List<Map<String, RDFNode>> rows = paginatedQuery.select(rdfStoreService, query);
    return paginatedQuery.convertSingleColumnUriToStringList(rows);
  }

  private boolean askTypeProperty(RdfStoreService rdfStoreService, String queryFile, Resource type, Resource property) {
    Map<String, String> parameters = Map.of("type", type.getURI(),
                                            "property", property.getURI());
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
