package zone.cogni.asquare.applicationprofile.shacl;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.access.shacl.Shacl;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Constraints;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.Not;
import zone.cogni.asquare.applicationprofile.rules.Or;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.sem.jena.RdfStatements;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.instanceOf;

public class ApplicationProfile2ShaclModel implements Function<ApplicationProfile, Model> {

  private static final Logger log = LoggerFactory.getLogger(ApplicationProfile2ShaclModel.class);

  static String prefix = "http://data.legilux.public.lu/resource/shacl/jolux#";

  @Override
  public Model apply(ApplicationProfile applicationProfile) {
    return applicationProfile.getTypes().values().stream()
            .map(this::getModel)
            .reduce(ModelFactory.createDefaultModel(), Model::add);
  }

  private Model getModel(ApplicationProfile.Type type) {
    return RdfStatements.asModel()
            .apply(new RdfStatements()
                           .add(getTypeShacl(type))
                           .add(getTypeRulesShacl(type))
                           .add(getAttributesShacl(type))
            );
  }


  private RdfStatements getTypeShacl(ApplicationProfile.Type type) {
    return new RdfStatements()
            .add(ResourceFactory.createResource(getShapeTypeUri(type)), RDF.type, Shacl.NodeShape);
  }

  private String getShapeTypeUri(ApplicationProfile.Type type) {
    return prefix + type.getClassId() + "Shape";
  }

  private RdfStatements getTypeRulesShacl(ApplicationProfile.Type type) {
    if (type.getRules().isEmpty()) return new RdfStatements();

    Preconditions.checkState(type.getRules().size() == 1);

    Rule rule = type.getRules().get(0);

    Preconditions.checkState(Constraints.class.isAssignableFrom(rule.getClass()),
                             "Rule '" + rule.getClass().getSimpleName() + "' ??");

    Constraints constraints = (Constraints) rule;
    Rule constraintsRule = constraints.getValue();

    // TODO improve
    if (Objects.equals(constraintsRule.getClass(), RdfType.class)) {
      RdfType rdfType = (RdfType) constraintsRule;
      return new RdfStatements()
              .add(ResourceFactory.createResource(getShapeTypeUri(type)),
                   Shacl.targetClass,
                   ResourceFactory.createResource(rdfType.getValue()));
    }

    throw new RuntimeException("Unknown rule.");
  }

  private RdfStatements getAttributesShacl(ApplicationProfile.Type type) {
    return type.getAttributes().values().stream()
            .map(attribute -> getAttributeShacl(type, attribute))
            .reduce(new RdfStatements(), RdfStatements::add);
  }

  private RdfStatements getAttributeShacl(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute) {
    Resource attributeNode = ResourceFactory.createResource();

    return getAttributeShacl(type, attribute, attributeNode)
            .add(getAttributeRulesShacl(type, attribute, attributeNode));
  }

  private RdfStatements getAttributeShacl(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute, Resource attributeNode) {
    return new RdfStatements()
            .add(ResourceFactory.createResource(getShapeTypeUri(type)), Shacl.property, attributeNode)
            .add(attributeNode, RDF.type, Shacl.PropertyShape)
            .add(attributeNode, Shacl.name, ResourceFactory.createTypedLiteral(attribute.getAttributeId()))
            .add(attributeNode, Shacl.path, ResourceFactory.createResource(attribute.getUri()));
  }

  private RdfStatements getAttributeRulesShacl(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute, Resource attributeNode) {
    return attribute.getRules().stream()
//            .peek(it -> System.out.println("attribute = " + attribute.getAttributeId()))
//            .peek(it -> System.out.println("rule      = " + it))
            .map(rule -> getAttributeRuleShacl(type, attribute, attributeNode, rule))
//            .peek(it -> System.out.println("x"))
//            .peek(System.out::println)
            .reduce(new RdfStatements(), RdfStatements::add);
  }

  private RdfStatements getAttributeRuleShacl(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute, Resource attributeNode, Rule rule) {
    return Match(rule).of(
            Case($(instanceOf(MinCardinality.class)), r -> getMinCount(attributeNode, r)),
            Case($(instanceOf(MaxCardinality.class)), r -> getMaxCount(attributeNode, r)),
            Case($(instanceOf(Range.class)), r -> getRange(type, attribute, attributeNode, r)),
            Case($(), r -> {
              throw new RuntimeException("Unknown rule for " + attribute.getAttributeId());
            })
    );
  }

  private RdfStatements getMinCount(Resource attributeNode, MinCardinality minCardinality) {
    return new RdfStatements()
            .add(attributeNode, Shacl.minCount, ResourceFactory.createTypedLiteral(minCardinality.getValue()));
  }

  private RdfStatements getMaxCount(Resource attributeNode, MaxCardinality maxCardinality) {
    return new RdfStatements()
            .add(attributeNode, Shacl.maxCount, ResourceFactory.createTypedLiteral(maxCardinality.getValue()));
  }

  private RdfStatements getRange(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute, Resource attributeNode, Range range) {
    return getRangeValue(type, attribute, attributeNode, range.getValue());
  }

  private RdfStatements getRangeValue(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute, Resource contextNode, Rule rangeValue) {
    return Match(rangeValue).of(
            Case($(instanceOf(ClassId.class)), r -> getClassId(type, contextNode, r)),
            Case($(instanceOf(Datatype.class)), r -> getDatatype(type, contextNode, r)),
            Case($(instanceOf(Or.class)), r -> getOr(type, attribute, contextNode, r)),
            Case($(instanceOf(And.class)), r -> getAnd(type, attribute, contextNode, r)),
            Case($(instanceOf(Not.class)), r -> getNot(type, attribute, contextNode, r)),
            Case($(), r -> {
              log.warn("Unknown rule for {}.", attribute.getAttributeId());
              return new RdfStatements();
//              throw new RuntimeException("Unknown rule for " + attribute.getAttributeId());
            })
    );
  }

  private RdfStatements getNot(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute, Resource attributeNode, Not not) {
    Resource notNode = ResourceFactory.createResource();

    return getRangeValue(type, attribute, notNode, not.getValue())
            .add(attributeNode, Shacl.not, notNode);
  }

  private RdfStatements getAnd(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute, Resource attributeNode, And and) {
    Resource andNode = ResourceFactory.createResource();

    RdfStatements result = new RdfStatements()
            .add(attributeNode, Shacl.and, andNode);

    List<RdfStatements> andList = and.getValue().stream()
            .map(rule -> getRangeValue(type, attribute, andNode, rule))
            .collect(Collectors.toList());

    result.add(andList.stream());

    List<Resource> roots = andList.stream()
            .map(this::getRoot)
            .collect(Collectors.toList());

    result.add(createList(andNode, roots));

    return result;
  }

  public static RdfStatements createList(Resource subject, Property property, List<Resource> list) {
    Resource root = ResourceFactory.createResource();
    RdfStatements result = createList(root, list);
    result.add(subject, property, root);
    return result;
  }

  private static RdfStatements createList(Resource root, List<Resource> list) {
    if (list.isEmpty()) throw new RuntimeException("Empty list not supported yet.");

    RdfStatements result = new RdfStatements();
    Iterator<Resource> members = list.iterator();

    result.add(root, RDF.first, members.next());

    Resource last = root;
    while (members.hasNext()) {
      Resource rest = ResourceFactory.createResource();
      result.add(rest, RDF.first, members.next());
      result.add(last, RDF.rest, rest);
      last = rest;
    }
    result.add(last, RDF.rest, RDF.nil);

    System.out.println("createList() = " );
    RdfStatements.asModel().apply(result).write(System.out, "ttl");

    return result;
  }

  private Resource getRoot(RdfStatements rdfStatements) {
    Set<Resource> subjects = rdfStatements.get().stream()
            .map(Statement::getSubject)
            .collect(Collectors.toSet());

    Set<Resource> objects = rdfStatements.get().stream()
            .map(Statement::getObject)
            .filter(RDFNode::isResource)
            .map(node -> (Resource) node)
            .collect(Collectors.toSet());

    subjects.removeAll(objects);

    return subjects.size() == 1 ? subjects.stream().findFirst().get()
                                : null;
  }

  private RdfStatements getOr(ApplicationProfile.Type type, ApplicationProfile.Attribute attribute, Resource attributeNode, Or or) {
    Resource orNode = ResourceFactory.createResource();

    RdfStatements result = new RdfStatements()
            .add(attributeNode, Shacl.or, orNode);

    List<RdfStatements> orList = or.getValue().stream()
            .map(rule -> getRangeValue(type, attribute, orNode, rule))
            .collect(Collectors.toList());

    result.add(orList.stream());

    List<Resource> roots = orList.stream()
            .map(this::getRoot)
            .collect(Collectors.toList());

    result.add(createList(orNode, roots));
    return result;
  }

  private RdfStatements getDatatype(ApplicationProfile.Type type, Resource attributeNode, Datatype datatype) {
    return new RdfStatements()
            .add(attributeNode, Shacl.datatype, ResourceFactory.createResource(datatype.getValue()));
  }

  private RdfStatements getClassId(ApplicationProfile.Type type, Resource resourceInContext, ClassId classId) {
    ApplicationProfile.Type attributeType = type.getApplicationProfile().getType(classId.getValue());

    return new RdfStatements()
            .add(getTypeRulesShacl(attributeType));
//            .add(resourceInContext, Shacl.classP, classId.getValue())
  }

}
