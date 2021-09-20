package zone.cogni.asquare.applicationprofile.owl.owl2ap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.control.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.Constants;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.model.builders.ApplicationProfileBuilder;
import zone.cogni.asquare.applicationprofile.model.builders.AttributeBuilder;
import zone.cogni.asquare.applicationprofile.model.builders.TypeBuilder;
import zone.cogni.asquare.applicationprofile.owl.model.rules.Cardinality;
import zone.cogni.asquare.applicationprofile.owl.model.rules.DisjointWith;
import zone.cogni.asquare.applicationprofile.owl.model.rules.EquivalentClass;
import zone.cogni.asquare.applicationprofile.owl.model.rules.EquivalentProperty;
import zone.cogni.asquare.applicationprofile.owl.model.rules.HasValue;
import zone.cogni.asquare.applicationprofile.owl.model.rules.InverseOf;
import zone.cogni.asquare.applicationprofile.owl.model.rules.MaxQualifiedCardinality;
import zone.cogni.asquare.applicationprofile.owl.model.rules.MinQualifiedCardinality;
import zone.cogni.asquare.applicationprofile.owl.model.rules.QualifiedCardinality;
import zone.cogni.asquare.applicationprofile.owl.model.rules.SomeValuesFrom;
import zone.cogni.asquare.applicationprofile.owl.model.rules.SubPropertyOf;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.Not;
import zone.cogni.asquare.applicationprofile.rules.Or;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.ResourceReference;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;
import zone.cogni.sem.jena.RdfStatements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;

public class Owl2SquareOwl implements Function<Model, ApplicationProfileDef> {

  private static final Logger log = LoggerFactory.getLogger(Owl2SquareOwl.class);

  private final String SUCCESS = "Successfully processed";

  private ApplicationProfileBuilder applicationProfileBuilder;
  private ApplicationProfileDef applicationProfile;

  private Model modelOriginal;
  private Model modelExtra;
  private Model model;

  private ModelSummary modelSummary;

  private final PrefixCcService prefixCcService;
  private ApplicationProfileNames applicationProfileNames;

  public Owl2SquareOwl(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;

  }

  @Override
  public ApplicationProfileDef apply(Model model) {
    this.applicationProfileBuilder = new ApplicationProfileBuilder().withPrefixCcService(prefixCcService);
    this.applicationProfile = applicationProfileBuilder.get();
    this.applicationProfileNames = new ApplicationProfileNames(prefixCcService, applicationProfile);

    return this.withModel(model)
               .build();
  }

  private Owl2SquareOwl withModel(Model inputModel) {
    modelOriginal = inputModel;
    modelExtra = ModelFactory.createDefaultModel();
    model = ModelFactory.createUnion(modelOriginal, modelExtra);
    modelSummary = new ModelSummary(inputModel);

    buildExtraModel();
//    model.write(System.out, "TURTLE");
//    if (null == null) throw new RuntimeException();
    return this;
  }

  private void buildExtraModel() {
    // owl:Thing a rdfs:Class => to keep inheritance simple! we derive from owl:Class to rdfs:Class
    modelExtra.add(OWL.Thing, RDF.type, RDFS.Class);

    // rdfs:subClassOf => rdfs:Class
    model.listStatements(null, RDFS.subClassOf, (RDFNode) null)
         .forEachRemaining(statement -> {
           modelExtra.add(statement.getSubject(), RDF.type, RDFS.Class);
         });

    // owl:Class => rdfs:Class
    model.listStatements(null, RDF.type, OWL.Class)
         .forEachRemaining(statement -> {
           modelExtra.add(statement.getSubject(), RDF.type, RDFS.Class);
         });

    // rdfs:Class => owl:subClassOf owl:Thing
    model.listStatements(null, RDF.type, RDFS.Class)
         .forEachRemaining(statement -> {
           modelExtra.add(statement.getSubject(), RDFS.subClassOf, OWL.Thing);
         });

    // rdfs:subClassOf and parent is owl:Class => owl:Class
    // TODO implement

    // remove silly statement
    modelExtra.remove(OWL.Thing, RDFS.subClassOf, OWL.Thing);

    // owl:ObjectProperty, owl:DatatypeProperty, owl:AnnotationProperty => rdf:Property
    Stream.of(OWL.ObjectProperty, OWL.DatatypeProperty, OWL.AnnotationProperty)
          .forEach(property -> {
            model.listStatements(null, RDF.type, property)
                 .forEachRemaining(statement -> {
                   modelExtra.add(statement.getSubject(), RDF.type, RDF.Property);
                 });
          });

    // rdf:Property without rdfs:domain => rdfs:domain owl:Thing
    model.listStatements(null, RDF.type, RDF.Property)
         .forEachRemaining(statement -> {
           Resource subject = statement.getSubject();
           if (!model.contains(subject, RDFS.domain)) {
             modelExtra.add(subject, RDFS.domain, OWL.Thing);
           }
         });

  }


  public ApplicationProfileDef build() {
    processModel();

    return applicationProfileBuilder.get();
  }

  private void processModel() {
    processOntology();

    processClasses(); /* ok */
    addSuperClasses(); // TODO verify cleanup
    addComplementOf();
    addEquivalentClasses(); /* ok */
    addDisjointWithClasses();

    // TODO verify methods (logic + cleanup)Pre
    getProperties().forEach(property -> processProperty(property, RDF.Property)); /* ok */

    addEquivalentProperties();/* ok */
    addInverseProperties(); /* ok */
    addSubProperties(); /* ok */

    addEquivalentClassRestrictions();
    applicationProfile.getTypeDefs().values().forEach(type -> {
      type.getAttributeDefs().values().forEach(attribute -> {
        addRange(type, attribute);

        /* ok */
        addMinCardinality(type, attribute);
        /* ok */
        addMaxCardinality(type, attribute);
        /* ok */
        addCardinality(type, attribute);

        addMinQualifiedCardinality(type, attribute);
        addMaxQualifiedCardinality(type, attribute);
        addQualifiedCardinality(type, attribute);
      });
    });

    addExtra();

    /* ok */
    ignoreProperty(RDFS.comment.getURI(),
                   RDFS.label.getURI(),
                   SKOS.altLabel.getURI(),
                   SKOS.editorialNote.getURI(),
                   OWL.versionInfo.getURI(),
                   "http://data.legilux.public.lu/resource/ontology/jolux#alignmentComment",
                   DCTerms.creator.getURI(),
                   DCTerms.title.getURI(),
                   OWL.imports.getURI()
                  );
    ignoreType(OWL.Ontology.getURI());

    model.write(System.out, "ttl");
    modelSummary.summarize();
  }

  private void processOntology() {
    List<Statement> ontologyStatements = model.listStatements(null, RDF.type, OWL.Ontology).toList();

    List<String> ontologyUris = ontologyStatements.stream()
                                                  .map(Statement::getSubject)
                                                  .map(Resource::getURI)
                                                  .collect(Collectors.toList());

    Preconditions.checkState(ontologyUris.size() == 1);
    modelSummary.add(SUCCESS, new RdfStatements().add(ontologyStatements));

    applicationProfile.setUri(ontologyUris.get(0));
  }

  private void processClasses() {
    processClassesOfType(RDFS.Class);
  }

  private void processClassesOfType(Resource classType) {
    getResourcesOfType(classType).forEach(classResource -> {

      System.out.println("classResource = " + classResource.getURI());

      String classId = applicationProfileNames.getClassId(classResource);

      TypeBuilder typeBuilder = new TypeBuilder()
              .withClassId(classId)
              .withRules(Stream.of(new RdfType(classResource.getURI()))
                               .collect(Collectors.toList()));

      applicationProfileBuilder.withType(typeBuilder);

      modelSummary.add(SUCCESS, new RdfStatements().add(classResource, RDF.type, classType));
    });
  }

  private String stripUri(String uri) {
    return uri.endsWith("#") || uri.endsWith("/") ? uri.substring(0, uri.length() - 1)
                                                  : uri;
  }

  private List<Resource> getResourcesOfType(Resource classType) {
    return model.listStatements(null, RDF.type, classType).toList().stream()
                .map(Statement::getSubject)
                .filter(RDFNode::isURIResource)
                .collect(Collectors.toList());
  }


  private List<Resource> getProperties() {
    return model.listStatements(null, RDF.type, RDF.Property).toList().stream()
                .map(Statement::getSubject)
                .collect(Collectors.toList());
  }

  private void processProperty(Resource propertyResource, Resource propertyType) {
    if (propertyResource.isAnon()) {
      // note: if we have cases we could try to support this case
      log.warn("Blank node as property not supported. Suggested type {}.", propertyType);
      modelSummary.add("Blank node as property not supported.",
                       new RdfStatements().add(getBlankNodeStatements(propertyResource)));
      return;
    }

    List<RDFNode> propertyDomains = getPropertyDomains(propertyResource).stream()
                                                                        .peek(domain -> {
                                                                          if (domain.isAnon()) {
                                                                            log.warn("Blank node as domain not supported. Property is '{}'", propertyResource.getURI());
                                                                            modelSummary.add("Blank node as domain not supported.",
                                                                                             new RdfStatements().add(getBlankNodeStatements(propertyResource)));
                                                                          }
                                                                        })
                                                                        .filter(domain -> !domain.isAnon())
                                                                        .collect(Collectors.toList());

    if (propertyDomains.isEmpty()) {
      log.error("Property without domain: '{}'.", propertyResource.getURI());
      modelSummary.add("Property without domain",
                       new RdfStatements().add(getBlankNodeStatements(propertyResource)));
      return;
    }

    if (propertyDomains.size() > 1) {
      // TODO might be possible to support this case
      log.error("Property with multiple domains not supported. Property is {}.", propertyResource.getURI());

      modelSummary.add("Property with multiple domains",
                       new RdfStatements().add(getBlankNodeStatements(propertyResource)));
      return;
    }

    Resource propertyDomain = propertyDomains.get(0).asResource();

    Option<ApplicationProfileDef.TypeDef> typeFromDomain = getTypeFromRdfType(propertyDomain.getURI());
    typeFromDomain
            .peek(type -> {
              if (type.hasAttributeDef(propertyResource.getLocalName())) {
                boolean sameProperty = Objects.equals(type.getAttributeDef(propertyResource.getLocalName()).getUri(), propertyResource.getURI());
                if (sameProperty) {
                  // not an issue; already know via a restriction
                  modelSummary.add(SUCCESS, new RdfStatements().add(propertyResource, RDFS.domain, propertyDomains));
                }
                else {
                  // TODO manage conflicting attribute
                  log.error("Same local name different namespaces. Uris {} and {}.", type.getAttributeDef(propertyResource.getLocalName()).getUri(), propertyResource.getURI());
                  modelSummary.add("Same local name different namespaces", new RdfStatements().add(getBlankNodeStatements(propertyResource)));
                }
              }
              else {
                AttributeBuilder attributeBuilder = new AttributeBuilder()
                        .withUri(propertyResource.getURI())
                        .withAttributeId(propertyResource.getLocalName());

                type.addAttributeDef(attributeBuilder.get());

                modelSummary.add(SUCCESS, new RdfStatements().add(propertyResource, RDFS.domain, propertyDomains));
              }
            })
            .onEmpty(() -> {
              log.warn("Property domain type not found. Property {} and domain type {}.", propertyResource.getURI(), propertyDomain);
              modelSummary.add("Property domain type not found", new RdfStatements().add(getBlankNodeStatements(propertyResource)));
            });
  }

  private void addSuperClasses() {
    applicationProfile.getTypeDefs().values()
                      .forEach(type -> getSuperClasses(type)
                              .forEach(superClass -> addSuperClass(type, superClass)));
  }

  private List<RDFNode> getSuperClasses(ApplicationProfileDef.TypeDef type) {
    return model.listStatements(createResource(getUri(type)), RDFS.subClassOf, (RDFNode) null).toList().stream()
                .map(Statement::getObject)
                .collect(Collectors.toList());
  }

  private void addSuperClass(ApplicationProfileDef.TypeDef type, RDFNode superClass) {
    Preconditions.checkState(superClass.isResource(), "Invalid node type: {}", superClass);

    boolean isRealSubclass = getTypeFromRdfType(superClass.asResource().getURI()).isDefined();
    boolean isOwlRestriction = superClass.asResource().hasProperty(RDF.type, OWL.Restriction);

    if (isRealSubclass) {
      addRealSuperClass(type, superClass);
    }
    else if (isOwlRestriction) {
      RestrictionType restrictionType = getRestrictionType(superClass.asResource());

      if (restrictionType == null) {
        log.error("Unknown type of owl:Restriction. Type is '{}'.", type.getDescription());
        logStatements(getBlankNodeStatements(superClass.asResource()));
        modelSummary.add("Unknown type of owl:Restriction",
                         new RdfStatements().add(getBlankNodeStatements(superClass.asResource())));
        return;
      }

      addOwlRestrictionSuperClass(type, superClass, restrictionType);

    }
    else {
      log.error("Blank node superclass must be an owl:Restriction. Type is '{}'", getStatements(superClass.asResource(), RDF.type, null, Statement::getObject));

      logStatements(getBlankNodeStatements(superClass.asResource()));
      modelSummary.add("Blank node superclass must be an owl:Restriction.",
                       new RdfStatements().add(getBlankNodeStatements(superClass.asResource())));
      return;
    }
  }

  private void logContextStatements(Resource resource) {
    log.info("Context for resource: {}", resource);
    log.info("Subject statements:   {}", model.listStatements(resource, null, (RDFNode) null).toList());
    log.info("Object statements:    {}", model.listStatements(null, null, resource).toList());
  }

  private void addOwlRestrictionSuperClass(ApplicationProfileDef.TypeDef type, RDFNode superClass, RestrictionType restrictionType) {
    Resource restriction = superClass.asResource();

    Resource onProperty = (Resource) Streams.stream(model.listStatements(restriction, OWL.onProperty, (RDFNode) null))
                                            .map(Statement::getObject).findFirst().get();

    ApplicationProfileDef.AttributeDef attribute = type.getAttributeDefByUri(onProperty.getURI())
                                                       .getOrElse(() -> {
                                                                    ApplicationProfileDef.AttributeDef newAttribute = new AttributeBuilder()
                                                                            .withUri(onProperty.getURI())
                                                                            .withAttributeId(onProperty.getLocalName()).get();

                                                                    type.addAttributeDef(newAttribute);
                                                                    return newAttribute;
                                                                  }
                                                                 );

    if (RestrictionType.someValuesFrom == restrictionType) {
      addSomeValuesFrom(type, attribute, RDFS.subClassOf, restriction);
    }

    if (Arrays.asList(RestrictionType.cardinality,
                      RestrictionType.minCardinality,
                      RestrictionType.maxCardinality).contains(restrictionType)) {
      if (restrictionType == RestrictionType.cardinality) addCardinality(type, attribute, restriction);
      else if (restrictionType == RestrictionType.minCardinality) addMinCardinality(type, attribute, restriction);
      else if (restrictionType == RestrictionType.maxCardinality) addMaxCardinality(type, attribute, restriction);
    }
    else if (Arrays.asList(RestrictionType.qualifiedCardinality,
                           RestrictionType.qualifiedMinCardinality,
                           RestrictionType.qualifiedMaxCardinality).contains(restrictionType)) {

      // TODO support anon for onType and onProperty??
      // TODO onDatatype??
      if (restrictionType == RestrictionType.qualifiedCardinality)
        addQualifiedCardinality(type, attribute, restriction);
      else if (restrictionType == RestrictionType.qualifiedMinCardinality)
        addMinQualifiedCardinality(type, attribute, restriction);
      else if (restrictionType == RestrictionType.qualifiedMaxCardinality)
        addMaxQualifiedCardinality(type, attribute, restriction);

    }
  }

  private void addRealSuperClass(ApplicationProfileDef.TypeDef type, RDFNode superClass) {
    ApplicationProfileDef.TypeDef superType = getTypeFromRdfType(superClass.asResource().getURI()).get();

    SubClassOf subClassOf = type.getRule(SubClassOf.class)
                                .getOrElse(() -> add(type, new SubClassOf()));

    subClassOf.getValue().add(superType.getClassId());

    modelSummary.add(SUCCESS,
                     new RdfStatements().add(createResource(getUri(type)), RDFS.subClassOf, superClass));
  }

  private void addSomeValuesFrom(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Property typeRelation, Resource restriction) {
    Resource onProperty = restriction.getPropertyResourceValue(OWL.onProperty);
    Resource someValuesFromResource = restriction.getPropertyResourceValue(OWL.someValuesFrom);

    buildRangeRule(attribute.getAttributeId(), someValuesFromResource)
            .peek(rule -> {
              add(attribute, new SomeValuesFrom(rule));

              modelSummary.add(SUCCESS, new RdfStatements()
                                       .add(createResource(getUri(type)), typeRelation, restriction)
                                       .add(restriction, RDF.type, OWL.Restriction)
                                       .add(restriction, OWL.onProperty, onProperty)
                                       .add(restriction, OWL.someValuesFrom, someValuesFromResource)
                                       .add(getBlankNodeStatements(someValuesFromResource))
                              );
            })
            .onEmpty(() -> {
              modelSummary.add("Unknown owl:someValuesFrom triples",
                               new RdfStatements()
                                       .add(createResource(getUri(type)), typeRelation, restriction)
                                       .add(restriction, RDF.type, OWL.Restriction)
                                       .add(restriction, OWL.onProperty, onProperty)
                                       .add(restriction, OWL.someValuesFrom, someValuesFromResource)
                                       .add(getBlankNodeStatements(someValuesFromResource))
                                       .add(model.listStatements(restriction, null, (RDFNode) null).toList()));
            });

    modelSummary.add("Unknown owl:someValuesFrom triples",
                     new RdfStatements().add(model.listStatements(restriction, null, (RDFNode) null).toList()));
  }

  private void logStatements(List<Statement> statements) {
    StringBuilder message = new StringBuilder();
    statements.forEach(statement -> {
      message.append("        ").append(statement.getSubject())
             .append(" ").append(statement.getPredicate())
             .append(" ").append(statement.getObject()).append("\n");
    });

    log.info("Statements: \n{}", message);
  }

  private List<Statement> getBlankNodeStatements(Resource root) {
    List<Statement> statements = new ArrayList<>();
    getBlankNodeStatements(root, statements);
    return statements;
  }

  private void getBlankNodeStatements(Resource rootOrBlankChildNode, List<Statement> statements) {
    List<Statement> nestedStatements = model.listStatements(rootOrBlankChildNode, null, (RDFNode) null).toList();
    statements.addAll(nestedStatements);
    statements.addAll(model.listStatements(null, null, rootOrBlankChildNode).toList());

    nestedStatements.stream()
                    .map(Statement::getObject)
                    .filter(RDFNode::isAnon)
                    .collect(Collectors.toList())
                    .forEach(nestedBlankNode -> getBlankNodeStatements(nestedBlankNode.asResource(), statements));
  }

  private void addExtra() {
    Model todoModel = modelSummary.getTodoModel();
    todoModel.listStatements().toList().stream()
             .forEach(statement -> {
               Resource subject = statement.getSubject();
               if (subject.isAnon()) {
                 // ignoring it for now
                 return;
               }

               String subjectUri = subject.getURI();

               // subject is ontology
               if (Objects.equals(applicationProfile.getUri(), subjectUri)) {
                 String property = prefixCcService.getShortForProperty(statement.getPredicate());
                 String value = statement.getObject().toString();
                 applicationProfile.addExtra(property, value);

                 modelSummary.add(SUCCESS, new RdfStatements().add(statement));
                 return;
               }

               // subject is type
               Optional<? extends ApplicationProfileDef.TypeDef> isMatchingType = applicationProfile.getTypeDefs().values().stream()
                                                                                                    .filter(type -> Objects.equals(getUri(type), subjectUri))
                                                                                                    .findFirst();
               isMatchingType.ifPresent(type -> {
                 String property = applicationProfileNames.getPropertyId(statement.getPredicate());
                 String value = statement.getObject().toString();
                 type.addExtra(property, value);

                 modelSummary.add(SUCCESS, new RdfStatements().add(statement));
                 return;
               });

               // all attributes matching subject
               applicationProfile.getTypeDefs().values().stream()
                                 .map(type -> type.getAttributeDefs().values())
                                 .flatMap(Collection::stream)
                                 .filter(attribute -> Objects.equals(attribute.getUri(), subjectUri))
                                 .forEach(attribute -> {
                                   String property = applicationProfileNames.getPropertyId(statement.getPredicate());
                                   String value = statement.getObject().toString();
                                   attribute.addExtra(property, value);

                                   modelSummary.add(SUCCESS, new RdfStatements().add(statement));
                                 });

             });
  }

  private void ignoreProperty(String... properties) {
    Set<Property> propertySet = getProperties(properties);

    List<Statement> removeStatements = model.listStatements().toList().stream()
                                            .filter(s -> propertySet.contains(s.getPredicate()))
                                            .collect(Collectors.toList());

    modelSummary.add("Ignored property", new RdfStatements().add(removeStatements));
  }

  private void ignoreType(String... types) {
    Arrays.asList(types).forEach(type -> {
      List<Statement> statements = model.listStatements(null, RDF.type, createResource(type)).toList();
      modelSummary.add("Ignored type", new RdfStatements().add(statements));
    });
  }

  private Set<Property> getProperties(String[] properties) {
    return Arrays.stream(properties)
                 .map(ResourceFactory::createProperty)
                 .collect(Collectors.toSet());
  }


  private void addQualifiedCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Resource restriction) {
    addQualifiedCardinalityOption(attribute, getQualifiedCardinality(type, attribute, OWL2.qualifiedCardinality, restriction));
  }

  private void addQualifiedCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute) {
    addQualifiedCardinalityOption(attribute, getQualifiedCardinality(type, attribute, OWL2.qualifiedCardinality));
  }

  private Option<Tuple3<Integer, Rule, RdfStatements>> addQualifiedCardinalityOption(ApplicationProfileDef.AttributeDef attribute, Option<Tuple3<Integer, Rule, RdfStatements>> qualifiedCardinality1) {
    return qualifiedCardinality1
            .peek(cardinality -> {
              Integer value = cardinality._1;
              Rule typeRule = cardinality._2;

              attribute.addRule(new QualifiedCardinality(value, typeRule));

              RdfStatements owlStatements = cardinality._3;
              modelSummary.add(SUCCESS, owlStatements);
            });
  }

  private void addMinQualifiedCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Resource restriction) {
    addMinQualifiedCardinalityOption(attribute, getQualifiedCardinality(type, attribute, OWL2.minQualifiedCardinality, restriction));
  }

  private void addMinQualifiedCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute) {
    addMinQualifiedCardinalityOption(attribute, getQualifiedCardinality(type, attribute, OWL2.minQualifiedCardinality));
  }

  private void addMinQualifiedCardinalityOption(ApplicationProfileDef.AttributeDef attribute, Option<Tuple3<Integer, Rule, RdfStatements>> qualifiedCardinality) {
    qualifiedCardinality
            .peek(cardinality -> {
              attribute.addRule(new MinQualifiedCardinality(cardinality._1, cardinality._2));

              RdfStatements owlStatements = cardinality._3;
              modelSummary.add(SUCCESS, owlStatements);
            });
  }

  private void addMaxQualifiedCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Resource restriction) {
    addMaxQualifiedCardinalityOption(attribute, getQualifiedCardinality(type, attribute, OWL2.maxQualifiedCardinality, restriction));
  }

  private void addMaxQualifiedCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute) {
    addMaxQualifiedCardinalityOption(attribute, getQualifiedCardinality(type, attribute, OWL2.maxQualifiedCardinality));
  }

  private void addMaxQualifiedCardinalityOption(ApplicationProfileDef.AttributeDef attribute, Option<Tuple3<Integer, Rule, RdfStatements>> qualifiedCardinality) {
    qualifiedCardinality
            .peek(cardinality -> {
              attribute.addRule(new MaxQualifiedCardinality(cardinality._1, cardinality._2));

              RdfStatements owlStatements = cardinality._3;
              modelSummary.add(SUCCESS, owlStatements);
            });
  }

  private Option<Tuple3<Integer, Rule, RdfStatements>> getQualifiedCardinality(ApplicationProfileDef.TypeDef type,
                                                                               ApplicationProfileDef.AttributeDef attribute,
                                                                               Property property) {
    List<RDFNode> restrictions = getRestrictions(type);

    List<RDFNode> cardinalities = restrictions.stream()
                                              .filter(restriction -> model.contains((Resource) restriction, OWL.onProperty, createResource(attribute.getUri())))
                                              .filter(restriction -> model.contains((Resource) restriction, property))
                                              .collect(Collectors.toList());

    if (cardinalities.isEmpty()) return Option.none();

    Preconditions.checkState(cardinalities.size() == 1);

    Resource qualifiedCardinalityRestriction = cardinalities.get(0).asResource();
    return getQualifiedCardinality(type, attribute, property, qualifiedCardinalityRestriction);
  }

  private Option<Tuple3<Integer, Rule, RdfStatements>> getQualifiedCardinality(ApplicationProfileDef.TypeDef type,
                                                                               ApplicationProfileDef.AttributeDef attribute,
                                                                               Property property,
                                                                               Resource restriction) {
    Literal cardinality = getObject(restriction, property).asLiteral();
    int value = cardinality.getInt();

    RdfStatements owlStatements = new RdfStatements()
            .add(createResource(getUri(type)), RDFS.subClassOf, restriction)
            .add(restriction, RDF.type, OWL.Restriction)
            .add(restriction, OWL.onProperty, createResource(attribute.getUri()))
            .add(restriction, property, cardinality);


    if (model.contains(restriction, OWL2.onDataRange)) {
      // TODO might be done "smarter"
      Resource datatype = getObject(restriction, OWL2.onDataRange).asResource();
      Rule rule = Datatype.datatype(datatype.getURI());

      owlStatements.add(restriction, OWL2.onDataRange, datatype);
//      if (rule.isEmpty()) {
//        log.error("Cannot determine range for qualified restriction on type {} and attribute {}", type, attribute);
//        return Option.none();
//      }

      List<Statement> unknownStatements = model.listStatements(restriction, null, (RDFNode) null).toList();
      unknownStatements.removeAll(owlStatements.get());

      modelSummary.add("Unknown statements for qualified cardinality restriction", new RdfStatements().add(unknownStatements));

      return Option.some(new Tuple3<>(value, rule, owlStatements));
    }
    else if (model.contains(restriction, OWL2.onClass)) {
      Option<Rule> rule = buildRangeRule(attribute.getAttributeId(), getObject(restriction, OWL2.onClass));
      if (rule.isEmpty()) {
        log.error("Cannot determine range for qualified restriction on type {} and attribute {}", type, attribute);
        return Option.none();
      }

      List<Statement> unknownStatements = model.listStatements(restriction, null, (RDFNode) null).toList();
      unknownStatements.removeAll(owlStatements.get());

      modelSummary.add("Unknown statements for qualified cardinality restriction", new RdfStatements().add(unknownStatements));
      // TODO fill cleanup (not finished yet)

      return Option.some(new Tuple3<>(value, rule.get(), owlStatements));
    }
    else {
      log.error("Qualified restriction is missing owl2:onClass or owl2:onDataRange. Type {} and attribute {}.", type, attribute);
      modelSummary.add("Qualified restriction is missing owl2:onClass or owl2:onDataRange", owlStatements);
      return Option.none();
    }
  }

  private void addCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Resource restriction) {
    addCardinalityOption(attribute, getCardinality(type, attribute, OWL.cardinality, restriction));
  }

  private void addCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute) {
    addCardinalityOption(attribute, getCardinality(type, attribute, OWL.cardinality));
  }

  private void addCardinalityOption(ApplicationProfileDef.AttributeDef attribute, Option<Tuple2<Integer, RdfStatements>> cardinalityOption) {
    cardinalityOption
            .peek(tuple -> {
              Integer value = tuple._1;
              RdfStatements owlStatements = tuple._2;

              attribute.addRule(new Cardinality(value));

              modelSummary.add(SUCCESS, owlStatements);
            });
  }


  private void addMaxCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Resource restriction) {
    addMaxCardinalityOption(attribute, getCardinality(type, attribute, OWL.maxCardinality, restriction));
  }

  private void addMaxCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute) {
    addMaxCardinalityOption(attribute, getCardinality(type, attribute, OWL.maxCardinality));
  }

  private void addMaxCardinalityOption(ApplicationProfileDef.AttributeDef attribute, Option<Tuple2<Integer, RdfStatements>> cardinality) {
    cardinality
            .peek(tuple -> {
              Integer value = tuple._1;
              RdfStatements owlStatements = tuple._2;

              add(attribute, new MaxCardinality(value));
              modelSummary.add(SUCCESS, owlStatements);
            });
  }

  private void addMinCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Resource restriction) {
    addMinCardinalityOption(attribute, getCardinality(type, attribute, OWL.minCardinality, restriction));
  }

  private void addMinCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute) {
    addMinCardinalityOption(attribute, getCardinality(type, attribute, OWL.minCardinality));
  }

  private void addMinCardinalityOption(ApplicationProfileDef.AttributeDef attribute, Option<Tuple2<Integer, RdfStatements>> cardinality) {
    cardinality
            .peek(tuple -> {
              Integer value = tuple._1;
              RdfStatements owlStatements = tuple._2;

              add(attribute, new MinCardinality(value));
              modelSummary.add(SUCCESS, owlStatements);
            });
  }

  private Option<Tuple2<Integer, RdfStatements>> getCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Property property) {

    List<RDFNode> restrictions = getRestrictions(type);

    List<RDFNode> cardinalities = restrictions.stream()
                                              .filter(restriction -> model.contains((Resource) restriction, OWL.onProperty, createResource(attribute.getUri())))
                                              .filter(restriction -> model.contains((Resource) restriction, property))
                                              .collect(Collectors.toList());

    if (cardinalities.isEmpty()) return Option.none();

    Preconditions.checkState(cardinalities.size() == 1);

    Resource restriction = cardinalities.get(0).asResource();
    return getCardinality(type, attribute, property, restriction);
  }

  private Option<Tuple2<Integer, RdfStatements>> getCardinality(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute, Property property, Resource restriction) {
    Literal cardinality = getObject(restriction, property).asLiteral();
    int value = cardinality.getInt();

    RdfStatements owlStatements = new RdfStatements()
            .add(createResource(getUri(type)), RDFS.subClassOf, restriction)
            .add(restriction, RDF.type, OWL.Restriction)
            .add(restriction, OWL.onProperty, createResource(attribute.getUri()))
            .add(restriction, property, cardinality);

    List<Statement> unknownStatements = model.listStatements(restriction, null, (RDFNode) null).toList();
    unknownStatements.removeAll(owlStatements.get());

    modelSummary.add("Unknown statements for cardinality restriction", new RdfStatements().add(unknownStatements));

    return Option.some(new Tuple2<>(value, owlStatements));
  }

  private void addEquivalentProperties() {
    List<Statement> statements = model.listStatements(null, OWL.equivalentProperty, (RDFNode) null).toList();
    List<Tuple2<Resource, Resource>> equivalentProperties = statements.stream()
                                                                      .peek(statement -> {
                                                                        if (statement.getSubject().isAnon() || statement.getObject().isAnon()) {
                                                                          log.warn("Blank node in owl:equivalentProperty. Relation between {} and {}.", statement.getSubject(), statement.getObject());
                                                                          modelSummary.add("Blank node in owl:equivalentProperty", new RdfStatements().add(statement));
                                                                        }
                                                                      })
                                                                      .filter(statement -> !statement.getSubject().isAnon() && !statement.getObject().isAnon())
                                                                      .map(statement -> new Tuple2<>(statement.getSubject(), statement.getObject().asResource()))
                                                                      .collect(Collectors.toList());

    equivalentProperties.forEach(equivalentPair -> {
      List<ApplicationProfileDef.AttributeDef> leftAttributes = findAttributes(equivalentPair._1);
      List<ApplicationProfileDef.AttributeDef> rightAttributes = findAttributes(equivalentPair._2);

      addEquivalentProperty(leftAttributes, equivalentPair._2);
      addEquivalentProperty(rightAttributes, equivalentPair._1);

      if (leftAttributes.isEmpty() && rightAttributes.isEmpty()) {
        log.warn("Cannot add equivalent property. No attributes in ontology found for either {} and {}", equivalentPair._1, equivalentPair._2);
      }

      modelSummary.add(SUCCESS, new RdfStatements().add(equivalentPair._1, OWL.equivalentProperty, equivalentPair._2));
    });

    cleanupEmptyEquivalentProperty();
  }

  private void addEquivalentProperty(List<ApplicationProfileDef.AttributeDef> attributes, Resource equivalentResource) {
    attributes.forEach(attribute -> {
      EquivalentProperty equivalentProperty = attribute.getRule(EquivalentProperty.class)
                                                       .getOrElse(() -> add(attribute, new EquivalentProperty(new ArrayList<>())));

      equivalentProperty.getValue().add(equivalentResource.getURI());
    });
  }

  private void cleanupEmptyEquivalentProperty() {
    applicationProfile.getTypeDefs().values().forEach(type -> {
      type.getAttributeDefs().values().forEach(attribute -> {
        attribute.getRule(EquivalentProperty.class)
                 .peek(equivalentProperty -> {
                   if (equivalentProperty.getValue().isEmpty()) {
                     attribute.removeRule(equivalentProperty);
                   }
                 });
      });
    });
  }

  private List<ApplicationProfileDef.AttributeDef> findAttributes(Resource resource) {
    Preconditions.checkState(resource.isURIResource());

    List<ApplicationProfileDef.AttributeDef> result = new ArrayList<>();

    applicationProfile.getTypeDefs().values().forEach(type -> {
      type.getAttributeDefs().values().forEach(attribute -> {
        if (Objects.equals(attribute.getUri(), resource.getURI())) {
          result.add(attribute);
        }
      });
    });

    return result;
  }

  private void addInverseProperties() {
    List<Statement> statements = model.listStatements(null, OWL.inverseOf, (RDFNode) null).toList();
    List<Tuple2<Resource, Resource>> inverseProperties = statements.stream()
                                                                   .peek(statement -> {
                                                                     if (statement.getSubject().isAnon() || statement.getObject().isAnon()) {
                                                                       log.warn("Cannot add inverse property. Blank node in relation between {} and {}.", statement.getSubject(), statement.getObject());
                                                                       new Cleanup().add(statement).accept(model);
                                                                     }

                                                                     System.out.println("s => " + statement.toString());
                                                                   })
                                                                   .filter(statement -> !statement.getSubject().isAnon() && !statement.getObject().isAnon())
                                                                   .map(statement -> new Tuple2<>(statement.getSubject(), statement.getObject().asResource()))
                                                                   .collect(Collectors.toList());

    inverseProperties.forEach(inversePair -> {
      List<ApplicationProfileDef.AttributeDef> leftAttributes = findAttributes(inversePair._1);
      List<ApplicationProfileDef.AttributeDef> rightAttributes = findAttributes(inversePair._2);

      addInverse(leftAttributes, inversePair._2);
      addInverse(rightAttributes, inversePair._1);

      if (leftAttributes.isEmpty() && rightAttributes.isEmpty()) {
        log.warn("Cannot add inverse property. No attributes in ontology found for either {} and {}", inversePair._1, inversePair._2);
      }

      modelSummary.add(SUCCESS, new RdfStatements().add(inversePair._1, OWL.inverseOf, inversePair._2));
    });
  }

  private void addInverse(List<ApplicationProfileDef.AttributeDef> attributes, Resource inverseOfResource) {
    attributes.forEach(attribute -> {
      InverseOf inverseOf = attribute.getRule(InverseOf.class)
                                     .getOrElse(() -> add(attribute, new InverseOf()));

      if (inverseOf.getValue() != null) {
        log.error("Cannot set more than one inverseOf on {}. Found {} and {}.", attribute.getAttributeId(), inverseOf.getValue(), inverseOfResource.getURI());
        return;
      }

      inverseOf.setValue(inverseOfResource.getURI());
    });
  }

  private void addSubProperties() {
    List<Statement> statements = model.listStatements(null, RDFS.subPropertyOf, (RDFNode) null).toList();
    List<Tuple2<Resource, Resource>> subProperties = statements.stream()
                                                               .peek(statement -> {
                                                                 if (statement.getSubject().isAnon() || statement.getObject().isAnon()) {
                                                                   log.warn("Cannot add subproperty. Blank node in relation between {} and {}.", statement.getSubject(), statement.getObject());
                                                                   new Cleanup().add(statement).accept(model);
                                                                 }
                                                               })
                                                               .filter(statement -> !statement.getSubject().isAnon() && !statement.getObject().isAnon())
                                                               .map(statement -> new Tuple2<>(statement.getSubject(), statement.getObject().asResource()))
                                                               .collect(Collectors.toList());

    subProperties.forEach(subProperty -> {
      List<ApplicationProfileDef.AttributeDef> subAttributes = findAttributes(subProperty._1);

      if (subAttributes.isEmpty()) {
        log.warn("Cannot add subproperty. No attributes in ontology found for {}.", subProperty._1);
      }

      addSubProperty(subAttributes, subProperty._2);

      modelSummary.add(SUCCESS, new RdfStatements().add(subProperty._1, RDFS.subPropertyOf, subProperty._2));
    });

  }

  private void addSubProperty(List<ApplicationProfileDef.AttributeDef> subAttributes, Resource superResource) {
    if (superResource.isAnon()) {
      log.error("Cannot add subproperty of blank node: {}", superResource);
    }

    subAttributes.forEach(attribute -> {
      SubPropertyOf subPropertyOf = attribute.getRule(SubPropertyOf.class)
                                             .getOrElse(() -> add(attribute, new SubPropertyOf(new ArrayList<>())));

      subPropertyOf.getValue().add(superResource.getURI());
    });
  }

  private void addComplementOf() {
    List<Resource> complementOfResources = model.listStatements(null, OWL.complementOf, (RDFNode) null).toList()
                                                .stream()
                                                .map(Statement::getSubject)
                                                .collect(Collectors.toList());

    complementOfResources.forEach(complementOfResource -> {
      ApplicationProfileDef.TypeDef type = getTypeFromRdfType(complementOfResource.getURI()).getOrElse((ApplicationProfileDef.TypeDef) null);
      // TODO get rid of it getClassId?

      type.addRule(new Range(buildComplementOfRule(type.getClassId(), complementOfResource)));
    });
  }

  private void addEquivalentClasses() {
    List<Tuple2<Resource, Resource>> equivalentClasses = model.listStatements(null, OWL.equivalentClass, (RDFNode) null).toList()
                                                              .stream()
                                                              .filter(statement -> {
                                                                boolean subjectRestriction = statement.getSubject().hasProperty(RDF.type, OWL.Restriction);
                                                                boolean objectRestriction = statement.getObject().isResource() && statement.getObject().asResource().hasProperty(RDF.type, OWL.Restriction);
                                                                return !subjectRestriction && !objectRestriction;
                                                              })
                                                              .filter(statement -> {
                                                                boolean blankNodePresent = statement.getSubject().isAnon() || statement.getObject().isAnon();

                                                                if (blankNodePresent) {
                                                                  log.warn("Cannot add equivalent class. Blank node in relation between {} and {}.", statement.getSubject(), statement.getObject());
                                                                  modelSummary.add("owl:equivalentClass with blank node", new RdfStatements().add(statement));
                                                                }

                                                                return !blankNodePresent;
                                                              })
                                                              .map(statement -> new Tuple2<>(statement.getSubject(), statement.getObject().asResource()))
                                                              .collect(Collectors.toList());

    equivalentClasses.forEach(equivalentPair -> {
      ApplicationProfileDef.TypeDef leftType = getTypeFromRdfType(equivalentPair._1.getURI()).getOrElse((ApplicationProfileDef.TypeDef) null);
      ApplicationProfileDef.TypeDef rightType = getTypeFromRdfType(equivalentPair._2.getURI()).getOrElse((ApplicationProfileDef.TypeDef) null);

      addEquivalentClass(leftType, equivalentPair._2);
      addEquivalentClass(rightType, equivalentPair._1);

      if (leftType == null && rightType == null) {
        log.warn("owl:equivalentClass types not found. Types {} and {}.", equivalentPair._1, equivalentPair._2);
        modelSummary.add("owl:equivalentClass types not found", new RdfStatements().add(equivalentPair._1, OWL.equivalentClass, equivalentPair._2));
        return;
      }

      modelSummary.add(SUCCESS, new RdfStatements().add(equivalentPair._1, OWL.equivalentClass, equivalentPair._2));
    });
  }

  private void addEquivalentClass(ApplicationProfileDef.TypeDef type, Resource equivalentResource) {
    if (type == null) return;

    EquivalentClass equivalentClass = type.getRule(EquivalentClass.class)
                                          .getOrElse(() -> add(type, new EquivalentClass(new ArrayList<>())));

    equivalentClass.getValue().add(equivalentResource.getURI());
  }

  private void addEquivalentClassRestrictions() {
    List<Tuple2<Resource, Resource>> equivalentClasses = model.listStatements(null, OWL.equivalentClass, (RDFNode) null).toList()
                                                              .stream()
                                                              .filter(statement -> {
                                                                boolean subjectRestriction = statement.getSubject().hasProperty(RDF.type, OWL.Restriction);
                                                                boolean objectRestriction = statement.getObject().isResource() && statement.getObject().asResource().hasProperty(RDF.type, OWL.Restriction);
                                                                return subjectRestriction || objectRestriction;
                                                              })
                                                              .map(statement -> new Tuple2<>(statement.getSubject(), statement.getObject().asResource()))
                                                              .collect(Collectors.toList());

    equivalentClasses.forEach(equivalentPair -> {
      Resource typeResource = getEquivalentTypeResource(equivalentPair);
      Resource restrictionResource = getEquivalentRestrictionResource(equivalentPair);

      // TODO better logging
      Preconditions.checkState(typeResource != null && restrictionResource != null);

      ApplicationProfileDef.TypeDef type = getTypeFromRdfType(typeResource.getURI()).get();

      RestrictionType restrictionType = getRestrictionType(restrictionResource);
      if (restrictionType == RestrictionType.hasValue) {
        addHasValueEquivalentClassRestriction(equivalentPair, restrictionResource, type);
      }
      // TODO other cases
      System.out.println("equivalentPair = " + equivalentPair);
    });
  }

  private void addHasValueEquivalentClassRestriction(Tuple2<Resource, Resource> equivalentPair, Resource restrictionResource, ApplicationProfileDef.TypeDef type) {
    RDFNode onProperty = getObject(restrictionResource, OWL.onProperty);
    if (onProperty.isURIResource()) {
      AttributeBuilder attributeBuilder = new AttributeBuilder()
              .withUri(onProperty.asResource().getURI())
              .withAttributeId(onProperty.asResource().getLocalName());

      ApplicationProfileDef.AttributeDef attribute = attributeBuilder.get();

      RDFNode hasValue = getObject(restrictionResource, OWL.hasValue);
      if (hasValue.isURIResource()) {
        add(attribute, new HasValue(hasValue.asResource().getURI()));
        type.addAttributeDef(attribute);

        modelSummary.add(SUCCESS, new RdfStatements()
                                 .add(equivalentPair._1, OWL.equivalentClass, equivalentPair._2)
                                 .add(restrictionResource, RDF.type, OWL.Restriction)
                                 .add(restrictionResource, OWL.onProperty, getObject(restrictionResource, OWL.onProperty))
                                 .add(restrictionResource, OWL.hasValue, getObject(restrictionResource, OWL.hasValue))
                        );
      }

      // TODO other cases

      if (hasValue.isResource() && hasValue.asResource().hasProperty(RDF.type)) {
        List<RDFNode> types = getStatements(hasValue.asResource(), RDF.type, null, Statement::getObject);

        Predicate<RDFNode> okTest = typeNode -> typeNode.isURIResource()
                                                && getTypeFromRdfType(typeNode.asResource().getURI()).isDefined();
        Tuple2<List<RDFNode>, List<RDFNode>> okFailLists = getOkFailLists(types, okTest);

        List<Rule> classIds = okFailLists._1.stream()
                                            .map(typeNode -> getTypeFromRdfType(typeNode.asResource().getURI()).get())
                                            .map(owlType -> new ClassId(owlType.getClassId()))
                                            .collect(Collectors.toList());

        if (classIds.size() == 1) {
          attribute.addRule(classIds.get(0));
        }
        else if (classIds.size() >= 1) {
          attribute.addRule(new And(classIds));
        }

        modelSummary.add(SUCCESS, new RdfStatements().add(hasValue.asResource(), RDF.type, okFailLists._1));

        okFailLists._2.forEach(typeNode -> {
          log.warn("owl:hasValue with unknown type. {} with type {}", hasValue.asResource().getURI(), typeNode.asResource().getURI());
        });
        modelSummary.add("owl:hasValue with unknown type", new RdfStatements().add(hasValue.asResource(), RDF.type, okFailLists._2));
      }
    }

    // TODO other cases

  }

  private Tuple2<List<RDFNode>, List<RDFNode>> getOkFailLists(List<RDFNode> types, Predicate<RDFNode> predicate) {
    return getOkFailLists(types, (okFail, typeNode) -> {
      if (predicate.test(typeNode)) okFail._1.add(typeNode);
      else okFail._2.add(typeNode);
    });
  }

  private Tuple2<List<RDFNode>, List<RDFNode>> getOkFailLists(List<RDFNode> types, BiConsumer<Tuple2<List<RDFNode>, List<RDFNode>>, RDFNode> okFailConsumer) {
    return types.stream().collect(
            () -> new Tuple2<>(new ArrayList<>(), new ArrayList<>()),
            okFailConsumer,
            (ign, ore) -> {
            }
                                 );
  }

  private Resource getEquivalentRestrictionResource(Tuple2<Resource, Resource> equivalentPair) {
    if (equivalentPair._1.hasProperty(RDF.type, OWL.Restriction)) return equivalentPair._1;
    if (equivalentPair._2.hasProperty(RDF.type, OWL.Restriction)) return equivalentPair._2;
    return null;
  }

  private Resource getEquivalentTypeResource(Tuple2<Resource, Resource> equivalentPair) {
    if (!equivalentPair._1.hasProperty(RDF.type, OWL.Restriction)
        && equivalentPair._1.isURIResource()
        && getTypeFromRdfType(equivalentPair._1.getURI()).isDefined()) {

      return equivalentPair._1;
    }

    if (!equivalentPair._2.hasProperty(RDF.type, OWL.Restriction)
        && equivalentPair._2.isURIResource()
        && getTypeFromRdfType(equivalentPair._2.getURI()).isDefined()) {

      return equivalentPair._2;
    }

    return null;
  }

  private void addDisjointWithClasses() {
    List<Statement> statements = model.listStatements(null, OWL.disjointWith, (RDFNode) null).toList();
    List<Tuple2<Resource, Resource>> disjointWithClasses = statements.stream()
                                                                     .peek(statement -> {
                                                                       if (statement.getSubject().isAnon() || statement.getObject().isAnon()) {
                                                                         log.warn("owl:disjointWith with a blank node. Between {} and {}.", statement.getSubject(), statement.getObject());
                                                                         modelSummary.add("owl:disjointWith with a blank node", new RdfStatements().add(statement));
                                                                       }
                                                                     })
                                                                     .filter(statement -> !statement.getSubject().isAnon() && !statement.getObject().isAnon())
                                                                     .map(statement -> new Tuple2<>(statement.getSubject(), statement.getObject().asResource()))
                                                                     .collect(Collectors.toList());

    disjointWithClasses.forEach(disjointPair -> {
      ApplicationProfileDef.TypeDef leftType = getTypeFromRdfType(disjointPair._1.getURI()).getOrElse((ApplicationProfileDef.TypeDef) null);
      ApplicationProfileDef.TypeDef rightType = getTypeFromRdfType(disjointPair._2.getURI()).getOrElse((ApplicationProfileDef.TypeDef) null);

      if (leftType == null && rightType == null) {
        log.warn("owl:disjointWith on unknown types. No types for {} and {}", disjointPair._1, disjointPair._2);
        modelSummary.add("owl:disjointWith on unknown types", new RdfStatements().add(disjointPair._1, OWL.disjointWith, disjointPair._2));
        return;
      }

      addDisjointWithClass(leftType, disjointPair._2);
      addDisjointWithClass(rightType, disjointPair._1);

      modelSummary.add(SUCCESS, new RdfStatements().add(disjointPair._1, OWL.disjointWith, disjointPair._2));
    });
  }

  private Option<ApplicationProfileDef.TypeDef> getTypeFromRdfType(String rdfTypeUri) {
    return Option.ofOptional(applicationProfile.getTypeDefs().values().stream()
                                               .filter(type -> type.getRules(RdfType.class).stream().anyMatch(rdfType -> Objects.equals(rdfType.getValue(), rdfTypeUri)))
                                               .findFirst());
  }

  private void addDisjointWithClass(ApplicationProfileDef.TypeDef type, Resource disjointResource) {
    if (type == null) return;

    DisjointWith disjointWith = type.getRule(DisjointWith.class)
                                    .getOrElse(() -> add(type, new DisjointWith(new ArrayList<>())));

    disjointWith.getValue().add(disjointResource.getURI());
  }

  private <T extends Rule> T add(ApplicationProfileDef.TypeDef type, T rule) {
    type.addRule(rule);
    return rule;
  }

  private <T extends Rule> T add(ApplicationProfileDef.AttributeDef attribute, T rule) {
    attribute.addRule(rule);
    return rule;
  }


  private void addRange(ApplicationProfileDef.TypeDef type, ApplicationProfileDef.AttributeDef attribute) {
    List<RDFNode> ranges = getStatements(createResource(attribute.getUri()), RDFS.range, null, Statement::getObject);

    if (ranges.isEmpty()) {
      modelSummary.add(SUCCESS, new RdfStatements()
                               .add(createResource(attribute.getUri()), RDF.type, getObjects(createResource(attribute.getUri()), RDF.type))
                      );
      return;
    }

    if (ranges.size() > 1) {
      log.error("Property cannot have multiple ranges. Property '{}' on type {}.", attribute.getAttributeId(), type.getDescription());
      modelSummary.add("Property cannot have multiple ranges", new RdfStatements()
                               .add(createResource(attribute.getUri()), RDFS.domain, createResource(getUri(type)))
                               .add(createResource(attribute.getUri()), RDFS.range, ranges)
                      );
      return;
    }

    RDFNode rangeType = ranges.get(0);

    List<RDFNode> types = getObjects(createResource(attribute.getUri()), RDF.type);
    setPropertyRange(attribute, rangeType);

    modelSummary.add(SUCCESS, new RdfStatements()
            .add(createResource(attribute.getUri()), RDFS.domain, createResource(getUri(type)))
            .add(createResource(attribute.getUri()), RDFS.range, rangeType)
            .add(createResource(attribute.getUri()), RDF.type, types));
  }

  private String getUri(ApplicationProfileDef.TypeDef updatableType) {
    return updatableType.getRule(RdfType.class).get().getValue();
  }

  private void setPropertyRange(ApplicationProfileDef.AttributeDef attribute, RDFNode range) {
    buildRangeRule(attribute.getAttributeId(), range)
            .peek(rule -> attribute.addRule(new Range(rule)));
  }

  private Option<Rule> buildRangeRule(String info, RDFNode range) {
    if (range.isLiteral()) return Option.none();

    Resource rangeResource = range.asResource();
    System.out.println("rangeResource = " + rangeResource);

    RangeType rangeType = getRangeType(rangeResource);
    if (rangeType == RangeType.unionOf) {
      return Option.some(buildUnionOfRule(info, rangeResource));
    }
    if (rangeType == RangeType.intersectionOf) {
      return Option.some(buildIntersectionOfRule(info, rangeResource));
    }
    if (rangeType == RangeType.complementOf) {
      return Option.some(buildComplementOfRule(info, rangeResource));
    }
    if (rangeType == RangeType.classId) {
      ApplicationProfileDef.TypeDef type = getTypeFromRdfType(rangeResource.getURI()).get();
      return Option.some(new ClassId(type.getClassId()));
    }
    if (rangeType == RangeType.datatype) {
      return Option.some(Datatype.datatype(rangeResource.getURI()));
    }
    if (rangeType == RangeType.resource) {
      return Option.some(new ResourceReference(rangeResource.getURI()));
    }

    if (Objects.equals(range, OWL.Thing)) return Option.none();

    // TODO might still be an issue
    log.error("Cannot determine range. On {}.", rangeResource);
    modelSummary.add("Cannot determine range", new RdfStatements().add(getBlankNodeStatements(rangeResource)));

    return Option.none();
  }

  private RangeType getRangeType(Resource range) {
    List<RDFNode> unionOfList = getObjects(range, OWL.unionOf);
    if (unionOfList.size() == 1 && unionOfList.get(0).isResource()) return RangeType.unionOf;

    List<RDFNode> intersectionOfList = getObjects(range, OWL.intersectionOf);
    if (intersectionOfList.size() == 1 && intersectionOfList.get(0).isResource()) return RangeType.intersectionOf;

    List<RDFNode> complementOfList = getObjects(range, OWL.complementOf);
    if (complementOfList.size() == 1 && complementOfList.get(0).isResource()) return RangeType.complementOf;

    if (range.isURIResource() && getTypeFromRdfType(range.getURI()).isDefined()) return RangeType.classId;

    if (range.isURIResource() && Constants.datatypes.contains(range.getURI())) return RangeType.datatype;

    if (range.isURIResource()) return RangeType.resource;

    return null;
  }

  private And buildIntersectionOfRule(String attributeId, Resource range) {
    Resource intersectionOfResource = getObject(range, OWL.intersectionOf).asResource();
    List<Resource> intersectionOfList = getRdfListOf(intersectionOfResource);

    modelSummary.add(SUCCESS, new RdfStatements()
            .add(range, OWL.intersectionOf, intersectionOfResource)
            .add(range, RDF.type, OWL.Class));

    return new And(getRangeList(attributeId, intersectionOfList));
  }

  private Or buildUnionOfRule(String attributeId, Resource range) {
    Resource unionOfResource = getObject(range, OWL.unionOf).asResource();
    List<Resource> unionOfList = getRdfListOf(unionOfResource);

    modelSummary.add(SUCCESS, new RdfStatements()
            .add(range, OWL.unionOf, unionOfResource)
            .add(range, RDF.type, OWL.Class));

    return new Or(getRangeList(attributeId, unionOfList));
  }

  private Not buildComplementOfRule(String attributeId, Resource range) {
    Resource complementOfResource = getObject(range, OWL.complementOf).asResource();


    modelSummary.add(SUCCESS, new RdfStatements()
            .add(range, OWL.complementOf, complementOfResource));

    return new Not(buildRangeRule(attributeId, complementOfResource).get());
  }

  private List<Rule> getRangeList(String attributeId, List<Resource> nodeList) {
    return nodeList.stream()
                   .map(node -> buildRangeRule(attributeId, node))
                   .filter(Option::isDefined)
                   .map(Option::get)
                   .collect(Collectors.toList());
  }

  private List<Resource> getRdfListOf(Resource resource) {
    List<Resource> result = new ArrayList<>();
    RdfStatements owlStatements = new RdfStatements();

    getRdfListOf(resource, result, owlStatements);

    modelSummary.add(SUCCESS, owlStatements);
    return result;
  }

  private void getRdfListOf(Resource resource, List<Resource> result, RdfStatements owlStatements) {
    System.out.println("resource = " + resource);
    if (Objects.equals(resource, RDF.nil)) return;

    RDFNode object = getObject(resource, RDF.first);
    result.add(object.asResource());
    System.out.println("object = " + object);

    RDFNode nextResource = getObject(resource, RDF.rest);
    System.out.println("nextResource = " + nextResource);

    owlStatements
            .add(resource, RDF.first, object)
            .add(resource, RDF.rest, nextResource);

    getRdfListOf(nextResource.asResource(), result, owlStatements);
  }

  private Collection<RDFNode> getPropertyDomains(Resource propertyResource) {
    return getStatements(propertyResource, RDFS.domain, null, Statement::getObject);
  }

  private RDFNode getObject(Resource s, Property p) {
    List<? extends RDFNode> objects = getObjects(s, p);
    Preconditions.checkState(objects.size() == 1);

    return objects.get(0);
  }

  private List<RDFNode> getObjects(Resource s, Property p) {
    return getStatements(s, p, null, Statement::getObject);
  }

  private List<RDFNode> getStatements(Resource s, Property p, RDFNode o, Function<Statement, RDFNode> map) {
    return getStatements(s, p, o, map, null);
  }

  private List<RDFNode> getStatements(Resource s, Property p, RDFNode o, Function<Statement, RDFNode> map, Predicate<RDFNode> filter) {
    return model.listStatements(s, p, o).toList().stream()
                .map(map)
                .filter(rdfNode -> filter == null || filter.test(rdfNode))
                .collect(Collectors.toList());
  }

  private List<RDFNode> getRestrictions(ApplicationProfileDef.TypeDef type) {
    List<RDFNode> candidates = model.listStatements(createResource(getUri(type)), RDFS.subClassOf, (RDFNode) null).toList().stream()
                                    .map(Statement::getObject)
                                    .collect(Collectors.toList());

    return candidates.stream()
                     .filter(node -> model.contains(node.asResource(), RDF.type, OWL.Restriction))
                     .collect(Collectors.toList());
  }

  private void findDetails(Resource propertyResource) {
    Streams.stream(model.listStatements(propertyResource, null, (RDFNode) null))
           .forEach(statement -> log.info("             ...                --- {} --> {}", statement.getPredicate().getURI(), statement.getObject()));

    Streams.stream(model.listStatements(null, null, propertyResource))
           .forEach(statement -> log.info("  {} -- {} --> ...", statement.getSubject(), statement.getPredicate().getURI()));
    ;

  }

  private RestrictionType getRestrictionType(Resource restriction) {
    Preconditions.checkState(model.contains(restriction, RDF.type, OWL.Restriction));

    List<Statement> propertyStatements = model.listStatements(restriction, OWL.onProperty, (RDFNode) null).toList();
    if (propertyStatements.size() != 1) {
      log.error("Problem in model. Restriction requires exactly one owl:onProperty.");
      return null;
    }

    if (propertyStatements.get(0).getObject().isLiteral()) {
      log.error("Problem in model. Restriction requires exactly one owl:onProperty of type resource.");
      return null;
    }

    if (restriction.hasProperty(OWL.hasValue)) {
      boolean oneHasValue = model.listStatements(restriction, OWL.hasValue, (RDFNode) null).toList().size() == 1;
      if (oneHasValue) return RestrictionType.hasValue;
    }

    if (restriction.hasProperty(OWL.someValuesFrom)) {
      boolean oneSomeValuesFrom = model.listStatements(restriction, OWL.someValuesFrom, (RDFNode) null).toList().size() == 1;
      if (oneSomeValuesFrom) return RestrictionType.someValuesFrom;
    }


    if (restriction.hasProperty(OWL.cardinality)) return RestrictionType.cardinality;
    if (restriction.hasProperty(OWL.minCardinality)) return RestrictionType.minCardinality;
    if (restriction.hasProperty(OWL.maxCardinality)) return RestrictionType.maxCardinality;

    if (restriction.hasProperty(OWL2.onDataRange) || restriction.hasProperty(OWL2.onClass)) {
      List<Statement> dataRangeStatements = model.listStatements(restriction, OWL2.onDataRange, (RDFNode) null).toList();
      List<Statement> classStatements = model.listStatements(restriction, OWL2.onClass, (RDFNode) null).toList();

      if (dataRangeStatements.size() + classStatements.size() != 1) {
        log.error("Problem in model. Restriction requires exactly one owl:onClass or one owl:onDataRange.");
        return null;
      }

      boolean wrongDataRange = !dataRangeStatements.isEmpty() && dataRangeStatements.get(0).getObject().isLiteral();
      boolean wrongClass = !classStatements.isEmpty() && classStatements.get(0).getObject().isLiteral();
      if (wrongDataRange || wrongClass) {
        log.error("Problem in model. Restriction requires exactly one owl:onClass or one owl:onDataRange of type resource.");
        return null;
      }

      if (restriction.hasProperty(OWL2.qualifiedCardinality)) return RestrictionType.qualifiedCardinality;
      if (restriction.hasProperty(OWL2.minQualifiedCardinality)) return RestrictionType.qualifiedMinCardinality;
      if (restriction.hasProperty(OWL2.maxQualifiedCardinality)) return RestrictionType.qualifiedMaxCardinality;
    }

    return null;
  }


  public enum RangeType {
    unionOf,
    intersectionOf,
    complementOf,
    datatype,
    resource,
    classId
  }


  public enum RestrictionType {
    cardinality,
    minCardinality,
    maxCardinality,
    qualifiedCardinality,
    qualifiedMinCardinality,
    qualifiedMaxCardinality,
    hasValue,
    someValuesFrom
  }

  private static class Cleanup implements Consumer<Model> {

    private List<Statement> statements = new ArrayList<>();

    public Cleanup add(Resource s, Property p, RDFNode o) {
      return add(ResourceFactory.createStatement(s, p, o));
    }

    public Cleanup add(Statement statement) {
      statements.add(statement);
      return this;
    }

    @Override
    public void accept(Model model) {
//      model.remove(statements);
    }
  }

  private static class ModelSummary {

    private final Model original;
    private final Model working;
    private final Map<String, List<Statement>> summary = new TreeMap<>();

    private ModelSummary(Model original) {
      this.original = ModelFactory.createDefaultModel();
      this.original.add(original);

      working = original;
    }

    public Model getTodoModel() {
      Model result = ModelFactory.createDefaultModel();
      result.add(original);
      summary.values().forEach(statements -> result.remove(statements));
      return result;
    }

    private void add(String message, RdfStatements owlStatements) {
      if (owlStatements.get().isEmpty()) return;

      if (!summary.containsKey(message)) summary.put(message, new ArrayList<>());
      summary.get(message).addAll(owlStatements.get());

      working.remove(owlStatements.get());
    }

    private void summarize() {
      int maxMessage = getMaxMessage();

      log.info("Summary of processed model.");
      log.info("    Original size:     {}", original.size());
      log.info("    Working size:      {}", working.size());
      log.info("    Processed triples: {}", original.size() - working.size());
      log.info("");
      summary.forEach((key, statements) -> {
        log.info("{} : {}", StringUtils.rightPad(key, maxMessage), StringUtils.leftPad(Integer.toString(statements.size()), 5));
//        log.debug(getStatementsMessage(statements));
      });
    }

    private int getMaxMessage() {
      return summary.keySet().stream().mapToInt(String::length).max().orElse(0);
    }

    private String getStatementsMessage(List<Statement> statements) {
      if (statements == null) return "";

      StringBuilder message = new StringBuilder();
      statements.forEach(statement -> {
        RDFNode object = statement.getObject();
        String objectString = object.toString();
        if (objectString.length() > 100) objectString = objectString.substring(0, 100) + " ...";

        message.append("        ").append(statement.getSubject())
               .append(" ").append(statement.getPredicate())
               .append(" ").append(objectString).append("\n");
      });

      return message.toString();
    }

  }

}
