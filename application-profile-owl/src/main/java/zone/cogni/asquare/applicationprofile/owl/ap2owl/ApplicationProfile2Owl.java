package zone.cogni.asquare.applicationprofile.owl.ap2owl;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.Extra;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.Not;
import zone.cogni.asquare.applicationprofile.rules.Or;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.sem.jena.RdfStatements;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Predicates.instanceOf;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.API.run;

public class ApplicationProfile2Owl implements Function<ApplicationProfile, Model> {

  private final RdfStatements rdfStatements = new RdfStatements();
  private PrefixCcService prefixCcService;
  private ApplicationProfile applicationProfile;

  public ApplicationProfile2Owl withPrefixCcService(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
    return this;
  }

  @Override
  public Model apply(ApplicationProfile applicationProfile) {
    this.applicationProfile = applicationProfile;

    processOntology();
    processExtra();
    this.applicationProfile.getTypes().values().forEach(this::processType);

    return RdfStatements.asModel().apply(rdfStatements);
  }

  private void processOntology() {
    rdfStatements.add(getApplicationProfileUri(applicationProfile), RDF.type, OWL.Ontology);
  }

  private void processExtra() {
    processExtra(getApplicationProfileUri(applicationProfile), applicationProfile.getExtra());
  }

  private Resource getApplicationProfileUri(ApplicationProfile applicationProfile) {
    return ResourceFactory.createResource(applicationProfile.getUri());
  }

  private void processExtra(Resource subject, Extra extra) {
    extra.getValue().forEach(rule -> {
      String property = rule.getProperty();
      String value = rule.getValue();

      rdfStatements.add(subject,
                        ResourceFactory.createProperty(prefixCcService.getPropertyForShort(property)),
                        ResourceFactory.createPlainLiteral(value));
    });
  }

  private void processType(ApplicationProfile.Type type) {
    processExtra(getTypeUri(type), type.getExtra());

    type.getRules().forEach(rule -> processTypeRule(type, rule));
    type.getAttributes().values().forEach(this::processAttribute);
  }

  private Resource getTypeUri(ApplicationProfile.Type type) {
    return ResourceFactory.createResource(type.getRule(RdfType.class).get().getValue());
  }

  private void processTypeRule(ApplicationProfile.Type type, Rule rule) {
    Match(rule).of(
            Case($(instanceOf(RdfType.class)), r -> run(() -> processRdfType((RdfType) r))),
            Case($(instanceOf(Range.class)), r -> run(() -> processTypeRange(type, (Range) r)))
    );
  }

  private void processRdfType(RdfType rdfType) {
    rdfStatements.add(ResourceFactory.createResource(rdfType.getValue()), RDF.type, OWL.Class);
  }

  private void processTypeRange(ApplicationProfile.Type type, Range range) {
    Rule rangeValue = range.getValue();

    List<Resource> tuple = processRule(type, rangeValue);
    // TODO
  }

  private List<Resource> processRule(ApplicationProfile.Type type, Rule value) {
    Resource parent = getTypeUri(type);
    return Match(value).of(
            Case($(instanceOf(ClassId.class)), r -> classId2tuple(type, (ClassId) r)),
            Case($(instanceOf(Not.class)), r -> processNot(type, parent, (Not) r)),
            Case($(instanceOf(And.class)), r -> and2tuple(type, (And) r)),
            Case($(instanceOf(Or.class)), r -> or2tuple(type, (Or) r))
    );
  }

  private List<Resource> classId2tuple(ApplicationProfile.Type type, ClassId classId) {
    Resource typeUri = getTypeUri(type.getApplicationProfile().getType(classId.getValue()));
    return Arrays.asList(typeUri);
  }

  private List<Resource> processNot(ApplicationProfile.Type type, Resource parent, Not not) {
    List<Resource> result = processRule(type, not.getValue());
    Preconditions.checkState(result.size() == 1, "Expected 1 'not' value when converting AP to OWL.");

    rdfStatements
            .add(parent, OWL.complementOf, result.get(0));
    return result;
  }

  private List<Resource> and2tuple(ApplicationProfile.Type type, And and) {
    return null;
  }

  private List<Resource> or2tuple(ApplicationProfile.Type type, Or or) {
    return null;
  }

  private void processAttribute(ApplicationProfile.Attribute attribute) {
    Resource attributeResource = ResourceFactory.createResource(attribute.getUri());

    processAttribute(attribute, attributeResource);
    processExtra(ResourceFactory.createResource(attribute.getUri()), attribute.getExtra());
    attribute.getRules().forEach(rule -> processAttributeRule(attribute, rule));
  }

  private void processAttribute(ApplicationProfile.Attribute attribute, Resource attributeResource) {
    rdfStatements
            .add(attributeResource, RDF.type, OWL.ObjectProperty)
            .add(attributeResource, RDFS.domain, getTypeUri(attribute.getType()));
  }

  private void processAttributeRule(ApplicationProfile.Attribute attribute, Rule rule) {
    Match(rule).of(
            Case($(instanceOf(MinCardinality.class)), r -> run(() -> processMinCardinality(attribute, (MinCardinality) r))),
            Case($(instanceOf(MaxCardinality.class)), r -> run(() -> processMaxCardinality(attribute, (MaxCardinality) r))),
            Case($(instanceOf(Range.class)), r -> run(() -> processAttributeRange(attribute, (Range) r)))
    );
  }

  private void processMinCardinality(ApplicationProfile.Attribute attribute, MinCardinality minCardinality) {
    Resource restriction = ResourceFactory.createResource();
    rdfStatements.add(getTypeUri(attribute.getType()), RDFS.subClassOf, restriction)
            .add(restriction, RDF.type, OWL.Restriction)
            .add(restriction, OWL.onProperty, ResourceFactory.createResource(attribute.getUri()))
            .add(restriction, OWL.minCardinality, ResourceFactory.createTypedLiteral(minCardinality.getValue()));
  }

  private void processMaxCardinality(ApplicationProfile.Attribute attribute, MaxCardinality maxCardinality) {
    Resource restriction = ResourceFactory.createResource();
    rdfStatements
            .add(getTypeUri(attribute.getType()), RDFS.subClassOf, restriction)
            .add(restriction, RDF.type, OWL.Restriction)
            .add(restriction, OWL.onProperty, ResourceFactory.createResource(attribute.getUri()))
            .add(restriction, OWL.maxCardinality, ResourceFactory.createTypedLiteral(maxCardinality.getValue()));
  }

  private void processAttributeRange(ApplicationProfile.Attribute attribute, Range range) {
    Resource attributeResource = ResourceFactory.createResource(attribute.getUri());
    List<Resource> result = processRule(attribute, attributeResource, range.getValue());

    rdfStatements.add(attributeResource, RDFS.range, result);
  }

  private List<Resource> processRule(ApplicationProfile.Attribute attribute, Resource parent, Rule value) {
    return Match(value).of(
            Case($(instanceOf(Datatype.class)), r -> processDatatype((Datatype) r)),
            Case($(instanceOf(Or.class)), r -> processOr(attribute, parent, (Or) r)),
            Case($(instanceOf(And.class)), r -> processAnd(attribute, parent, (And) r)),
            Case($(instanceOf(Not.class)), r -> processNot(attribute, parent, (Not) r)),
            Case($(instanceOf(ClassId.class)), r -> processClassId((ClassId) r))
    );
  }

  private List<Resource> processClassId(ClassId classId) {
    ApplicationProfile.Type type = applicationProfile.getType(classId.getValue());
    return Collections.singletonList(getTypeUri(type));
  }

  private List<Resource> processNot(ApplicationProfile.Attribute attribute, Resource parent, Not not) {
    Resource notResource = ResourceFactory.createResource();

    List<Resource> result = processRule(attribute, notResource, not.getValue());
    Preconditions.checkState(result.size() == 1);

    rdfStatements
            .add(notResource, RDF.type, OWL.Class)
            .add(notResource, OWL.complementOf, result.get(0));

    return Collections.singletonList(notResource);
  }


  private List<Resource> processAnd(ApplicationProfile.Attribute attribute, Resource parent, And and) {
    List<Rule> list = and.getValue();

    Resource root = processNestedAndOr(attribute, parent, list, OWL.intersectionOf);
    return Collections.singletonList(root);
  }

  private List<Resource> processOr(ApplicationProfile.Attribute attribute, Resource parent, Or or) {
    List<Rule> list = or.getValue();
    Resource root = processNestedAndOr(attribute, parent, list, OWL.unionOf);

    return Collections.singletonList(root);
  }

  private Resource processNestedAndOr(ApplicationProfile.Attribute attribute, Resource parent, List<Rule> list, Property andOr) {
    // process list
    List<Resource> andOrResources = list.stream()
            .map(rule -> processRule(attribute, parent, rule))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    // add resources as list
    Resource listRoot = addList(andOrResources);

    Resource andOrResource = ResourceFactory.createResource();
    rdfStatements
            .add(andOrResource, andOr, listRoot)
            .add(andOrResource, RDF.type, OWL.Class);

    return andOrResource;
  }

  private Resource addList(List<Resource> listResources) {
    Model defaultModel = ModelFactory.createDefaultModel();
    RDFList list = defaultModel.createList(listResources.iterator());

    defaultModel.listStatements().forEachRemaining(rdfStatements::add);
    return list.asResource();
  }

  private List<Resource> processDatatype(Datatype datatype) {
    Resource datatypeResource = ResourceFactory.createResource(datatype.getValue());
    return Collections.singletonList(datatypeResource);
  }

}
