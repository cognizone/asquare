package zone.cogni.asquare.access.shacl;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

public interface Shacl {

  String NS = "http://www.w3.org/ns/shacl#";

  // types
  Resource NodeShape = ResourceFactory.createResource(NS + "NodeShape");
  Resource PropertyShape = ResourceFactory.createResource(NS + "PropertyShape");
  Resource ValidationReport = ResourceFactory.createResource(NS + "ValidationReport");
  Resource ValidationResult = ResourceFactory.createResource(NS + "ValidationResult");

  Resource AndConstraintComponent = ResourceFactory.createResource(NS + "AndConstraintComponent");
  Resource DatatypeConstraintComponent = ResourceFactory.createResource(NS + "DatatypeConstraintComponent");
  Resource HasValueConstraintComponent = ResourceFactory.createResource(NS + "HasValueConstraintComponent");
  Resource MaxCountConstraintComponent = ResourceFactory.createResource(NS + "MaxCountConstraintComponent");
  Resource MinCountConstraintComponent = ResourceFactory.createResource(NS + "MinCountConstraintComponent");
  Resource NotConstraintComponent = ResourceFactory.createResource(NS + "NotConstraintComponent");
  Resource OrConstraintComponent = ResourceFactory.createResource(NS + "OrConstraintComponent");
  Resource NodeConstraintComponent = ResourceFactory.createResource(NS + "NodeConstraintComponent");
  Resource ClassConstraintComponent = ResourceFactory.createResource(NS + "ClassConstraintComponent");
  Resource PropertyShapeComponent = ResourceFactory.createResource(NS + "PropertyShapeComponent");
  Resource LanguageInConstraintComponent = ResourceFactory.createResource(NS + "LanguageInConstraintComponent");

  // properties
  Property and = createProperty(NS + "and");
  Property classP = createProperty(NS + "class");
  Property conforms = createProperty(NS + "conforms");
  Property datatype = createProperty(NS + "datatype");
  Property details = createProperty(NS + "details");
  Property focusNode = createProperty(NS + "focusNode");
  Property inversePath = createProperty(NS + "inversePath");
  Property minCount = createProperty(NS + "minCount");
  Property maxCount = createProperty(NS + "maxCount");
  Property name = createProperty(NS + "name");
  Property nodeKind = createProperty(NS + "nodeKind");
  Property not = createProperty(NS + "not");
  Property or = createProperty(NS + "or");
  Property path = createProperty(NS + "path");
  Property property = createProperty(NS + "property");
  Property result = createProperty(NS + "result");
  Property resultMessage = createProperty(NS + "resultMessage");
  Property resultPath = createProperty(NS + "resultPath");
  Property resultSeverity = createProperty(NS + "resultSeverity");
  Property sourceConstraintComponent = createProperty(NS + "sourceConstraintComponent");
  Property targetClass = createProperty(NS + "targetClass");
  Property value = createProperty(NS + "value");

  interface NodeKind {
    Resource IRI = createResource(NS + "IRI");
    Resource Literal = createResource(NS + "Literal");
    Resource BlankNode = createResource(NS + "BlankNode");
    Resource IRIOrLiteral = createResource(NS + "IRIOrLiteral");
    Resource BlankNodeOrIRI = createResource(NS + "BlankNodeOrIRI");
    Resource BlankNodeOrLiteral = createResource(NS + "BlankNodeOrLiteral");
  }

  interface Severity {
    Resource Info = ResourceFactory.createResource(NS + "Info");
    Resource Warning = ResourceFactory.createResource(NS + "Warning");
    Resource Violation = ResourceFactory.createResource(NS + "Violation");
  }

}
