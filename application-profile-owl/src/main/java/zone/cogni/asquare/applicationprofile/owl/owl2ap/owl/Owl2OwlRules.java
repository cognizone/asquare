package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.AllValuesFrom;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.DatatypePropertyReference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.ExactQualifiedCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.HasValue;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.Literal;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.MaxQualifiedCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.MinQualifiedCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.ObjectPropertyReference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlClassReference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlClassReferenceCandidate;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlEquivalentClass;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlExactCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlExtra;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlMaxCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlMinCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlOntology;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlSubClassOf;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.PropertyDomain;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.PropertyRange;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.PropertyReference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.PropertyReferenceCandidate;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.QualifiedCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.RdfNode;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.Reference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.SomeValuesFrom;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.Range;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Owl2OwlRules implements Function<Model, OwlRules> {

  private static final Logger log = LoggerFactory.getLogger(Owl2OwlRules.class);

  private Model model;
  private OwlRules owlRules;

  @Override
  public OwlRules apply(Model model) {
    this.model = new ExpandOwlModel().apply(model);
    this.owlRules = new OwlRules(model);

    matchSubjects();

    return owlRules;
  }

  private void matchSubjects() {
    model.listSubjects().toList().forEach(this::matchSubject);
  }

  private void matchSubject(Resource subject) {
    if (isOntology(subject)) {
      owlRules.add(new OwlOntology(new Reference(subject)));
    }
    else if (isPartOfList(subject)) {
      // ignore, list are processed together
    }
    else if (isOwlRestriction(subject)) {
      // ignore, is processed somewhere else
    }
    else if (isClassType(subject) &&                                // ex. owl:Class
             subject.isAnon() &&                                    // blank node
             hasOneStatement(null, RDFS.range, subject)) {  // ... rdfs:range <subject>
      // skipping range matching for now
    }
    else if (isClassType(subject) && subject.isURIResource()) {
      matchClass(subject);
    }
    else if (isAnyTypeOfProperty(subject)) {
      matchProperty(subject);
    }
    else if (isPossibleProperty(subject)) {
      owlRules.add(new PropertyReferenceCandidate(subject));
      matchPropertyProperties(subject);
    }
    else if (isPossibleClass(subject)) {
      owlRules.add(new OwlClassReferenceCandidate(subject));
      matchClassProperties(subject);
    }
    else {
      // TODO log problem?!
      log.warn("Insufficient data to handle subject: {}", subject);
    }
  }

  private boolean isOntology(Resource subject) {
    // TODO warn for other statements?
    return hasOneStatement(subject, RDF.type, OWL.Ontology);
  }

  private boolean isPartOfList(Resource subject) {
    return hasStatement(subject, RDF.rest, null);
  }

  private boolean isOwlRestriction(Resource subject) {
    // TODO warn for other statements?
    return hasOneStatement(subject, RDF.type, OWL.Restriction);
  }

  private void matchRestriction(Resource classResource, Resource restriction) {
    if (!hasOneStatement(restriction, OWL.onProperty, null)) {
      log.error("Problem in model. Restriction requires exactly one owl:onProperty.");
      return;
    }

    if (!hasOneResourceObject(restriction, OWL.onProperty)) {
      log.error("Problem in model. Restriction requires exactly one owl:onProperty of type resource.");
      return;
    }

    if (restriction.hasProperty(OWL.hasValue)) {
      matchHasValueRestriction(restriction);
    }
    else if (restriction.hasProperty(OWL.someValuesFrom)) {
      matchSomeValuesFromRestriction(restriction);
    }
    else if (restriction.hasProperty(OWL.allValuesFrom)) {
      matchAllValuesFromRestriction(restriction);
    }
    else if (restriction.hasProperty(OWL.cardinality)) {
      addCardinalityRestriction(restriction,
                                OWL.cardinality,
                                new OwlExactCardinality(new OwlClassReferenceCandidate(classResource)));
    }
    else if (restriction.hasProperty(OWL.minCardinality)) {
      addCardinalityRestriction(restriction,
                                OWL.minCardinality,
                                new OwlMinCardinality(new OwlClassReferenceCandidate(classResource)));
    }
    else if (restriction.hasProperty(OWL.maxCardinality)) {
      addCardinalityRestriction(restriction,
                                OWL.maxCardinality,
                                new OwlMaxCardinality(new OwlClassReferenceCandidate(classResource)));
    }
    else if (restriction.hasProperty(OWL2.qualifiedCardinality)) {
      addQualifiedCardinalityRestriction(restriction,
                                         OWL2.qualifiedCardinality,
                                         new ExactQualifiedCardinality(new OwlClassReferenceCandidate(classResource)));
    }
    else if (restriction.hasProperty(OWL2.minQualifiedCardinality)) {
      addQualifiedCardinalityRestriction(restriction,
                                         OWL2.minQualifiedCardinality,
                                         new MinQualifiedCardinality(new OwlClassReferenceCandidate(classResource)));
    }
    else if (restriction.hasProperty(OWL2.maxQualifiedCardinality)) {
      addQualifiedCardinalityRestriction(restriction,
                                         OWL2.maxQualifiedCardinality,
                                         new MaxQualifiedCardinality(new OwlClassReferenceCandidate(classResource)));
    }
    else {
      log.error("Unable to match restriction to correct rule: {}", restriction);
    }
  }

  private void matchHasValueRestriction(Resource restriction) {
    boolean isValid = hasOneResourceObject(restriction, OWL.hasValue) &&
                      hasOneResourceObject(restriction, OWL.onProperty);

    getWeirdStatements(restriction, RDF.type, OWL.hasValue, OWL.onProperty)
            .ifPresent(statements -> log.warn("Weird statements for owl:hasValue restriction: {}", statements));

    if (!isValid) return;

    Resource hasValue = restriction.getPropertyResourceValue(OWL.hasValue);
    Resource onProperty = restriction.getPropertyResourceValue(OWL.onProperty);

    // ignore for now
    if (hasValue.isAnon() || onProperty.isAnon()) {
      log.error("Support for blank hasValue {} or onProperty {} is not present.", hasValue, onProperty);
      return;
    }

    owlRules.add(new HasValue(new Reference(onProperty), new Reference(hasValue)));
  }

  private boolean isAnyTypeOfProperty(Resource subject) {
    return isObjectProperty(subject) ||
           isDatatypeProperty(subject) ||
           isAnnotationProperty(subject) ||
           isProperty(subject);
  }


  private boolean isClassType(Resource subject) {

    List<Statement> statements = model.listStatements(subject, RDF.type, (RDFNode) null).toList();

    boolean result = statements.stream().anyMatch(isClassPredicate());
    if (result) {
      List<RDFNode> weirdTypes = statements.stream()
                                           .filter(isClassPredicate().negate())
                                           .map(Statement::getObject)
                                           .collect(Collectors.toList());

      if (!weirdTypes.isEmpty())
        log.error("Expected just rdfs:Class or owl:Class. Found: {}", weirdTypes);
    }

    return result;
  }

  private Predicate<Statement> isClassPredicate() {
    Predicate<Statement> isOwlClass = s -> s.getObject().equals(OWL.Class);
    Predicate<Statement> isRdfsClass = s -> s.getObject().equals(RDFS.Class);
    return isOwlClass.or(isRdfsClass);
  }

  private boolean isObjectProperty(Resource subject) {
    return hasOneStatement(subject, RDF.type, OWL.ObjectProperty);
  }

  private boolean isDatatypeProperty(Resource subject) {
    return hasOneStatement(subject, RDF.type, OWL.DatatypeProperty);
  }

  private boolean isAnnotationProperty(Resource subject) {
    return hasOneStatement(subject, RDF.type, OWL.AnnotationProperty);
  }

  private boolean isProperty(Resource subject) {
    return hasOneStatement(subject, RDF.type, RDF.Property);
  }

  private boolean isPossibleProperty(Resource subject) {
    return subject.isURIResource() && Character.isLowerCase(subject.getLocalName().charAt(0));
  }

  private boolean isPossibleClass(Resource subject) {
    return subject.isURIResource() && Character.isUpperCase(subject.getLocalName().charAt(0));
  }


  private void matchSomeValuesFromRestriction(Resource restriction) {
    boolean isValid = hasOneResourceObject(restriction, OWL.someValuesFrom) &&
                      hasOneResourceObject(restriction, OWL.onProperty);

//    getWeirdStatements(restriction, RDF.type, OWL.someValuesFrom, OWL.onProperty)
//            .ifPresent(statements -> log.warn("Weird statements for someValuesFrom restriction: {}", statements));

    if (!isValid) return;

    Resource onProperty = restriction.getPropertyResourceValue(OWL.onProperty);
    Resource someValuesFrom = restriction.getPropertyResourceValue(OWL.someValuesFrom);

    // ignore for now
    if (someValuesFrom.isAnon() || onProperty.isAnon()) {
      log.error("Support for blank someValuesFrom {} or onProperty {} is not present.", someValuesFrom, onProperty);
      return;
    }

    owlRules.add(new SomeValuesFrom(new Reference(onProperty), new Reference(someValuesFrom)));
  }

  private void matchAllValuesFromRestriction(Resource restriction) {
    boolean isValid = hasOneResourceObject(restriction, OWL.allValuesFrom) &&
                      hasOneResourceObject(restriction, OWL.onProperty);

//    getWeirdStatements(restriction, RDF.type, OWL.allValuesFrom, OWL.onProperty)
//            .ifPresent(statements -> log.warn("Weird statements for allValuesFrom restriction: {}", statements));

    if (!isValid) return;

    Resource onProperty = restriction.getPropertyResourceValue(OWL.onProperty);
    Resource allValuesFrom = restriction.getPropertyResourceValue(OWL.allValuesFrom);

    // ignore for now
    if (allValuesFrom.isAnon() || onProperty.isAnon()) {
      log.error("Support for blank someValuesFrom {} or onProperty {} is not present.", allValuesFrom, onProperty);
      return;
    }

    owlRules.add(new AllValuesFrom(new Reference(onProperty), new Reference(allValuesFrom)));
  }

  private void addCardinalityRestriction(Resource restriction,
                                         Property cardinalityProperty,
                                         OwlCardinality cardinalityRestriction) {
    boolean isValid = hasOneLiteralObject(restriction, cardinalityProperty);

    if (!isValid) return;

    // TODO weird statements

    Resource onProperty = restriction.getPropertyResourceValue(OWL.onProperty);
    int cardinality = getLiteralObject(restriction, cardinalityProperty).getInt();

    cardinalityRestriction.setProperty(new PropertyReferenceCandidate(onProperty));
    cardinalityRestriction.setCardinality(cardinality);
    owlRules.add(cardinalityRestriction);
  }

  private void addQualifiedCardinalityRestriction(Resource restriction,
                                                  Property cardinalityProperty,
                                                  QualifiedCardinality cardinalityRestriction) {
    boolean classQualification = hasOneResourceObject(restriction, OWL2.onClass);
    boolean datatypeQualification = hasOneResourceObject(restriction, OWL2.onDatatype);
    boolean isValid = hasOneResourceObject(restriction, cardinalityProperty) &&
                      (classQualification || datatypeQualification);

    if (!isValid) return;

    // TODO weird statements

    Resource onProperty = restriction.getPropertyResourceValue(OWL.onProperty);
    int cardinality = getLiteralObject(restriction, cardinalityProperty).getInt();
    Rule qualification = classQualification
                         ? new Range(new Reference(getResourceObject(restriction, OWL2.onClass)))
                         : new Range(new Datatype(getResourceObject(restriction, OWL2.onDatatype).getURI()));

    cardinalityRestriction.setValue(new Reference(onProperty));
    cardinalityRestriction.setCardinality(cardinality);
    cardinalityRestriction.setQualification(qualification);

    owlRules.add(cardinalityRestriction);
  }

  private void matchClass(Resource subject) {
    owlRules.add(new OwlClassReference(subject));

    matchClassProperties(subject);
  }

  private void matchClassProperties(Resource subject) {
    getPropertiesOfResource(subject)
            .forEach(property -> matchClassProperty(subject, property));
  }

  private void matchClassProperty(Resource subject, Property property) {
    if (property.equals(RDF.type)) {
      // ignore: already processed ofc
    }
    else if (property.equals(RDFS.subClassOf)) {
      matchSubClassOf(subject);
    }
    else if (property.equals(OWL.equivalentClass)) {
      owlRules.add(getEquivalentClasses(subject));
    }
    else if (property.equals(OWL.disjointWith)) {
      // TODO !!
    }
    else {
      matchExtra(subject, property);
    }
  }

  private void matchSubClassOf(Resource subClass) {
    getSubClassOfResources(subClass)
            .forEach(superClass -> {
              if (isOwlRestriction(superClass)) {
                matchRestriction(subClass, superClass);
              }
              else if (superClass.isURIResource()) {
                owlRules.add(new OwlSubClassOf(new OwlClassReference(subClass),
                                               new OwlClassReference(superClass)));
              }
              else {
                log.warn("Unable to handle subClassOf for subject {} and object {}.", subClass, superClass);
              }
            });
    }

  private List<Resource> getSubClassOfResources(Resource subject) {
    return subject.listProperties(RDFS.subClassOf).toList()
                  .stream()
                  .map(Statement::getObject)
                  .peek(object -> {
                    if (object.isLiteral())
                      log.error("owl:subClassOf cannot be a literal: {}", object);
                  })
                  .map(RDFNode::asResource)
                  .collect(Collectors.toList());
  }

  private List<OwlEquivalentClass> getEquivalentClasses(Resource subject) {
    List<Statement> statements = model.listStatements(subject, OWL.equivalentClass, (RDFNode) null).toList();
    return statements
            .stream()
            .map(Statement::getObject)
            .peek(object -> {
              if (object.isLiteral()) log.error("owl:equivalentClass cannot be a literal: {}", object);
              if (object.isAnon()) log.warn("owl:equivalentClass blank node not yet supported: {}", subject);
            })
            .filter(RDFNode::isURIResource)
            .map(object -> new OwlEquivalentClass(new OwlClassReference(subject),
                                                  new OwlClassReference(object.asResource()))
                )
            .collect(Collectors.toList());
  }

  private void matchProperty(Resource subject) {
    boolean isInvalidCombination = isDatatypeProperty(subject) && isObjectProperty(subject);
    if (isInvalidCombination) {
      log.error("Cannot be a datatype and object property at the same time: {}", subject);
      return;
    }

    owlRules.add(newPropertyReference(subject));
    matchPropertyProperties(subject);
  }

  private PropertyReference newPropertyReference(Resource subject) {
    return isDatatypeProperty(subject) ? new DatatypePropertyReference(subject)
                                       : isObjectProperty(subject) ? new ObjectPropertyReference(subject)
                                                                   : new PropertyReference(subject);
  }

  private void matchPropertyProperties(Resource property) {
    getPropertiesOfResource(property)
            .forEach(p -> matchPropertyProperties(property, p));
  }

  private Collection<Property> getPropertiesOfResource(Resource subject) {
    return model.listStatements(subject, null, (RDFNode) null).toList()
                .stream()
                .map(Statement::getPredicate)
                .collect(Collectors.toSet());
  }

  private void matchPropertyProperties(Resource subject, Property property) {
    if (property.equals(RDF.type)) {
      // ignore, already processed
    }
    else if (property.equals(OWL.inverseOf)) {
      log.error("rdfs:inverseOf is not supported anymore: {}", subject);
    }
    else if (property.equals(RDFS.subPropertyOf)) {
      log.error("rdfs:subPropertyOf is not supported anymore: {}", subject);
    }
    else if (property.equals(RDFS.domain)) {
      matchDomain(subject);
    }
    else if (property.equals(RDFS.range)) {
      matchRange(subject);
    }
    else {
      // default fallback for "unknown" properties
      matchExtra(subject, property);
    }
  }

  private void matchDomain(Resource subject) {
    List<Rule> ranges = getRangeRules(getObjects(subject, RDFS.domain));
    ifListConvertToAnd(ranges)
            .ifPresent(range -> {
              owlRules.add(new PropertyDomain(new PropertyReference(subject), range));
            });
  }

  private List<Rule> getRangeRules(List<RDFNode> domainObjects) {
    return domainObjects
            .stream()
            .peek(object -> {
              if (object.isLiteral()) {
                log.error("Property {} has rdfs:Literal in domain: {}", object.asLiteral());
              }
            })
            .filter(RDFNode::isResource)
            .map(RDFNode::asResource)
            .map(this::getRange)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
  }

  private Optional<Rule> ifListConvertToAnd(List<Rule> ranges) {
    if (ranges.isEmpty()) return Optional.empty();
    if (ranges.size() == 1) return Optional.of(ranges.get(0));

    return Optional.of(new And(ranges));
  }

  private void matchRange(Resource subject) {
    List<Rule> ranges = getRangeRules(getObjects(subject, RDFS.range));
    ifListConvertToAnd(ranges)
            .ifPresent(range -> {
              owlRules.add(new PropertyRange(new PropertyReference(subject), range));
            });
  }

  private Optional<Rule> getRange(Resource rangeResource) {
    if (rangeResource.isAnon()) {
      if (!isClassType(rangeResource)) {
        String message = "Blank node range is missing owl:Class or rdfs:Class, has properties: {}";
        log.error(message, getPropertiesOfResource(rangeResource));
        return Optional.empty();
      }
      else if (rangeResource.hasProperty(OWL.unionOf)) {
        return matchUnionOf(rangeResource);
      }
      else if (rangeResource.hasProperty(OWL.intersectionOf)) {
        return matchIntersectionOf(rangeResource);
      }
      else {
        log.error("Unknown range with properties: {}", getPropertiesOfResource(rangeResource));
        return Optional.empty();
      }
    }
    else {
      return Optional.of(new OwlClassReferenceCandidate(rangeResource));
    }
  }

  private Optional<Rule> matchUnionOf(Resource rangeResource) {
    return Optional.empty();
  }

  private Optional<Rule> matchIntersectionOf(Resource rangeResource) {
    return Optional.empty();
  }

  private void matchExtra(Resource subject, Property property) {
    List<RdfNode> rdfNodes = getObjects(subject, property)
            .stream()
            .map(rdfNode -> rdfNode.isLiteral() ? new Literal(rdfNode.asLiteral())
                                                : new Reference(rdfNode.asResource()))
            .collect(Collectors.toList());

    owlRules.add(new OwlExtra(new Reference(subject),
                              new Reference(property.getURI()),
                              rdfNodes));
  }

  private List<PropertyReference> getPropertyReferences(Resource subject, Property property) {
    return getObjects(subject, property)
            .stream()
            .peek(object -> {
              if (object.isLiteral())
                log.error("{} cannot be a literal: {}", property, object.asLiteral());
            })
            .map(RDFNode::asResource)
            .map(PropertyReferenceCandidate::new)
            .collect(Collectors.toList());
  }

  //----------------------------------------------------------------------------------
  //
  //                     utility methods
  //
  //----------------------------------------------------------------------------------

  private boolean hasOneStatement(Resource subject, Property property, RDFNode object) {
    return model.listStatements(subject, property, object).toList().size() == 1;
  }

  private boolean hasStatement(Resource subject, Property property, RDFNode object) {
    return !model.listStatements(subject, property, object).toList().isEmpty();
  }

  private List<RDFNode> getObjects(Resource subject, Property property) {
    return subject.listProperties(property).toList()
                  .stream()
                  .map(Statement::getObject)
                  .collect(Collectors.toList());
  }

  private org.apache.jena.rdf.model.Resource getResourceObject(Resource subject, Property property) {
    Preconditions.checkState(hasOneResourceObject(subject, property));

    return model.listStatements(subject, property, (RDFNode) null).toList()
                .get(0)
                .getObject()
                .asResource();
  }

  private org.apache.jena.rdf.model.Literal getLiteralObject(Resource subject, Property property) {
    Preconditions.checkState(hasOneLiteralObject(subject, property));

    return model.listStatements(subject, property, (RDFNode) null).toList()
                .get(0)
                .getObject()
                .asLiteral();

  }

  private boolean hasOneLiteralObject(Resource subject, Property property) {
    if (!hasOneStatement(subject, property, null)) return false;

    return model.listStatements(subject, property, (RDFNode) null).toList()
                .get(0)
                .getObject()
                .isLiteral();
  }

  private boolean hasOneResourceObject(Resource subject, Property property) {
    if (!hasOneStatement(subject, property, null)) return false;

    return model.listStatements(subject, property, (RDFNode) null).toList()
                .get(0)
                .getObject()
                .isResource();
  }


  private void logContextStatements(Resource resource) {
    log.info("Context for resource: {}", resource);
    log.info("    Subject statements:   {}", model.listStatements(resource, null, (RDFNode) null).toList());
    log.info("    Object statements:    {}", model.listStatements(null, null, resource).toList());
  }

  private Optional<List<Statement>> getWeirdStatements(Resource resource, Property... ignoredProperties) {
    List<Property> properties = Arrays.asList(ignoredProperties);
    List<Statement> result = resource.listProperties().toList()
                                     .stream()
                                     .filter(statement -> !properties.contains(statement.getPredicate()))
                                     .collect(Collectors.toList());
    return result.isEmpty() ? Optional.empty()
                            : Optional.of(result);
  }

}
