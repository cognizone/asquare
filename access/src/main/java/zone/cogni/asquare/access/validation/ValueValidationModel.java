package zone.cogni.asquare.access.validation;

import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import zone.cogni.asquare.access.util.PropertyPathUriMapper;
import zone.cogni.asquare.access.util.RdfParserUtils;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Constraints;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.EmbeddedClassId;
import zone.cogni.asquare.applicationprofile.rules.InScheme;
import zone.cogni.asquare.applicationprofile.rules.LanguageIn;
import zone.cogni.asquare.applicationprofile.rules.LiteralValue;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MaxLangCardinality;
import zone.cogni.asquare.applicationprofile.rules.MemberOf;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinLangCardinality;
import zone.cogni.asquare.applicationprofile.rules.Not;
import zone.cogni.asquare.applicationprofile.rules.Or;
import zone.cogni.asquare.applicationprofile.rules.PropertyPath;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.ResourceReference;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;
import zone.cogni.sem.jena.RdfStatements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Predicates.instanceOf;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.anyOf;

/**
 * @Deprecated use instead{@link ValueValidation}
 */
public class ValueValidationModel {

  public static Function<Rule, ValidationResult> withoutReport(Model model,
                                                               Object value,
                                                               ApplicationProfile applicationProfile) {
    return rule -> getValidationResult(model, value, applicationProfile, rule, new ValidationResult.Conforming());
  }

  public static Function<Rule, ValidationResult> withReport(Model model,
                                                            Object value,
                                                            ApplicationProfile applicationProfile) {
    return rule -> getValidationResult(model, value, applicationProfile, rule, new ValidationResult.Reporting());
  }

  private static ValidationResult getValidationResult(Model model, Object value, ApplicationProfile applicationProfile, Rule rule, ValidationResult validationResult) {
    ValueValidationModel valueValidation = new ValueValidationModel(validationResult, applicationProfile, model, value);
    valueValidation.validateRule(rule);
    return valueValidation.validationResult;
  }

  private final ValidationResult validationResult;
  private final ApplicationProfile applicationProfile;
  private final Model model;
  private final Object value;

  private ValueValidationModel(ValidationResult validationResult, ApplicationProfile applicationProfile, Model model, Object value) {
    this.validationResult = validationResult;
    this.applicationProfile = applicationProfile;
    this.model = model;
    this.value = value;
  }

  private Function<EmbeddedClassId, RdfStatements> validateEmbeddedClassId(Set<String> ongoingClassValidations) {

    return embeddedClassId -> {
      Objects.requireNonNull(value);

      ApplicationProfile.Type type = applicationProfile.getType(embeddedClassId.getValue());

      // TODO implement !!

      return new RdfStatements();
    };
  }

  private Function<ClassId, RdfStatements> validateClassId(Set<String> ongoingClassValidations) {
    return classId -> {
      Objects.requireNonNull(value);

      ApplicationProfile.Type type = applicationProfile.getType(classId.getValue());

      // class validation is ongoing, skip
      if (ongoingClassValidations.contains(type.getClassId())) {
        // todo skip or throw exception?
        return new RdfStatements();
      }

      // start class validation
      ongoingClassValidations.add(type.getClassId());

      RdfStatements validationResult = new RdfStatements();

      Preconditions.checkState(value instanceof RDFNode);
      RDFNode node = (RDFNode) value;
      if (node.isLiteral()) {
        String message = "Class rule violated. " +
                         "Literal value '" + getValueAsString() + "' is not an instance of type '" + type.getDescription() + "'";

        validationResult = new ValidationResultBuilder()
                .withMessage(message)
                .withSourceConstraintComponent(SHACLM.NodeConstraintComponent).get();
      }
      else {
        Option<Constraints> constraints = type.getRule(Constraints.class);
        if (constraints.isDefined()) {
          validationResult = validateClassConstraints(constraints.get().getValue(), type, validationResult, ongoingClassValidations);
        }
        else {
          List<Rule> rules = type.getRules();
          if (!rules.isEmpty()) {
            validationResult = validateClassConstraints(new And(rules), type, validationResult, ongoingClassValidations);
          }
        }


      }

      // class validation is finished
      ongoingClassValidations.remove(type.getClassId());

      return validationResult;
    };
  }

  private RdfStatements validateClassConstraints(Rule classConstraintRule, ApplicationProfile.Type type, RdfStatements validationResult, Set<String> ongoingClassValidations) {
    RdfStatements classValidationResult = getRangeValidationResult(value, classConstraintRule, ongoingClassValidations);
    if (!classValidationResult.isEmpty()) {
      String message = "Class rule violated. " +
                       "Resource '" + getValueAsString() + "' is not an instance of type '" + type.getDescription() + "'";

      validationResult = new ValidationResultBuilder()
              .withMessage(message)
              .withDetails(classValidationResult)
              .withSourceConstraintComponent(SHACLM.NodeConstraintComponent).get();
    }
    return validationResult;
  }

  private Function<RdfType, RdfStatements> validateRdfType() {
    return rdfType -> {
      Objects.requireNonNull(value);

      Preconditions.checkState(value instanceof RDFNode);
      RDFNode node = (RDFNode) value;

      if (node.isLiteral()) {
        if (Objects.equals(rdfType.getValue(), RDFS.Literal.getURI())) return new RdfStatements();

        String message = "RdfType rule violated. " +
                         "Literal value '" + getValueAsString() + "' is not a resource having rdf:type '" + rdfType.getValue() + "'";

        return failure(message, SHACLM.ClassConstraintComponent);
      }

      if (Objects.equals(rdfType.getValue(), RDFS.Resource.getURI())) return new RdfStatements();

      if (model.contains(node.asResource(), RDF.type, model.createResource(rdfType.getValue()))) return new RdfStatements();

      String message = "RdfType rule violated. " +
                       "Expected resource having rdf:type '" + rdfType.getValue() + "'. Resource '" + getValueAsString() + "' does not have the expected rdf:type.";

      return failure(message, SHACLM.ClassConstraintComponent);
    };
  }

  private Function<Or, RdfStatements> validateOr(Set<String> ongoingClassValidations) {
    return or -> {
      Objects.requireNonNull(value);

      List<RdfStatements> failedMathes = new ArrayList<>();
      boolean unionOfOk = or.getValue().stream()
              .map(rule -> {
                RdfStatements rangeValidationResult = getRangeValidationResult(value, rule, ongoingClassValidations);
                if (!rangeValidationResult.isEmpty()) failedMathes.add(rangeValidationResult);
                return rangeValidationResult;
              })
              .anyMatch(RdfStatements::isEmpty);

      if (unionOfOk) return new RdfStatements();

      String message = "Or rule violated for '" + getValueAsString() + "'.";

      return new ValidationResultBuilder()
              .withMessage(message)
              .withSourceConstraintComponent(SHACLM.OrConstraintComponent)
              .withDetails(failedMathes).get();
    };
  }

  private String getValueAsString() {
    Preconditions.checkState(value instanceof RDFNode);
    RDFNode node = (RDFNode) value;
    if (node.isLiteral()) return ((Literal) node).getLexicalForm();
    if (node.isURIResource()) return node.asResource().getURI();
    if (node.isAnon()) return node.asResource().getId().getLabelString();

    throw new RuntimeException("Should never get here!");
  }

  private Function<Not, RdfStatements> validateNot(Set<String> ongoingClassValidations) {
    return not -> {
      Objects.requireNonNull(value);

      RdfStatements validationResult = getRangeValidationResult(value, not.getValue(), ongoingClassValidations);
      if (!validationResult.isEmpty()) return new RdfStatements();

      // todo add details
      return failure("Not rule violated for '" + getValueAsString() + "'.", SHACLM.NotConstraintComponent);
    };
  }

  private Function<And, RdfStatements> validateAnd(Set<String> ongoingClassValidations) {
    return intersectionOf -> {
      Objects.requireNonNull(value);

      List<RdfStatements> problems = intersectionOf.getValue().stream()
              .map(rule -> getRangeValidationResult(value, rule, ongoingClassValidations))
              .filter(rdfStatements -> !rdfStatements.isEmpty())
              .collect(Collectors.toList());

      if (problems.isEmpty()) return new RdfStatements();

      String message = "And rule violated for '" + getValueAsString() + "'.";

      return new ValidationResultBuilder()
              .withMessage(message)
              .withSourceConstraintComponent(SHACLM.AndConstraintComponent)
              .withDetails(problems)
              .get();
    };
  }

  private Function<SubClassOf, RdfStatements> validateSubClassOf(Set<String> ongoingClassValidations) {
    return intersectionOf -> {
      Objects.requireNonNull(value);

      return new zone.cogni.sem.jena.RdfStatements();
    };
  }

  private Function<InScheme, RdfStatements> validateInScheme() {
    return inScheme -> {
      Objects.requireNonNull(value);

      Preconditions.checkState(value instanceof RDFNode);
      RDFNode node = (RDFNode) value;

      if (node.isLiteral()) {
        String message = "InScheme rule violated. " +
                         "Literal value '" + getValueAsString() + "' is not a resource having skos:inScheme property '" + inScheme.getValue() + "'";

        return failure(message, SHACLM.NodeKindConstraintComponent);
      }

      if (model.contains(node.asResource(), SKOS.inScheme, model.createResource(inScheme.getValue()))) return new RdfStatements();

      String message = "InScheme rule violated. " +
                       "Expected resource having skos:inScheme property '" + inScheme.getValue() + "'. Resource '" + getValueAsString() + "' does not have the expected skos:inScheme property.";

      return failure(message, SHACLM.NodeKindConstraintComponent);
    };
  }

  private Function<MemberOf, RdfStatements> validateMemberOf() {
    return memberOf -> {
      Objects.requireNonNull(value);

      Preconditions.checkState(value instanceof RDFNode);
      RDFNode node = (RDFNode) value;

      if (node.isLiteral()) {
        String message = "MemberOf rule violated. " +
                         "Literal value '" + getValueAsString() + "' is not a resource targeted by a skos:member property from '" + memberOf.getValue() + "'";

        return failure(message, SHACLM.NodeKindConstraintComponent);
      }

      Resource object = node.asResource().isURIResource() ? model.createResource(node.asResource().getURI()) : model.createResource(node.asResource().getId());
      if (model.contains(model.createResource(memberOf.getValue()), SKOS.member, object)) return new RdfStatements();

      String message = "MemberOf rule violated. " +
                       "Expected resource targeted by a skos:member property from '" + memberOf.getValue() + "'. Resource '" + getValueAsString() + "' is not the target of the expected skos:member property.";

      return failure(message, SHACLM.NodeKindConstraintComponent);
    };
  }

  private Function<LiteralValue, RdfStatements> validateLiteralValue() {
    return literalValue -> {
      Objects.requireNonNull(value);

      boolean isLiteral = value instanceof Literal;

      if (!isLiteral) {
        String message = "Value check violated. " +
                         "Expected literal value '" + literalValue.getValue() + "' but type was '" + value.getClass().getSimpleName() + "'.";
        return failure(message, SHACLM.HasValueConstraintComponent);
      }

      Literal literal = (Literal) value;

      Literal expectedLiteral = RdfParserUtils.parseLiteral(model, literalValue.getValue());
      if (expectedLiteral.equals(literal)) return new RdfStatements();

      String message = "Value check violated. " +
                       "Expected literal value '" + expectedLiteral + "' but actual value is '" + literal.toString() + "'.";

      return failure(message, SHACLM.HasValueConstraintComponent);
    };
  }

  private Function<PropertyPath, RdfStatements> validatePropertyPath() {
    return propertyPath -> {
      Objects.requireNonNull(value);

      Preconditions.checkState(value instanceof RDFNode);
      RDFNode node = (RDFNode) value;

      if (node.isLiteral()) {
        String message = "PropertyPath rule violated. " +
                         "Literal value '" + getValueAsString() + "' is not a resource with property path '" + propertyPath.getPath() + "'";

        return failure(message, SHACLM.NodeKindConstraintComponent);
      }

      Resource resource = node.asResource().isURIResource() ? model.createResource(node.asResource().getURI()) : model.createResource(node.asResource().getId());

      if (model.contains(resource, model.createProperty(PropertyPathUriMapper.getUri(propertyPath.getPath())), RdfParserUtils.parseRdfNode(model, propertyPath.getValue()))) return new RdfStatements();

      String message = "PropertyPath rule violated. " +
                       "Expected resource having property path '" + propertyPath.getPath() + "' with value '" + propertyPath.getValue() + "'. Resource '" + getValueAsString() + "' is missing expected property path value.";

      return failure(message, SHACLM.NodeKindConstraintComponent);
    };
  }

  private Function<ResourceReference, RdfStatements> validateResourceReference() {
    return resourceReference -> {
      Objects.requireNonNull(value);

      boolean isResource = value instanceof Resource && ((Resource) value).isURIResource();

      if (!isResource) {
        String message = "Value check violated. " +
                         "Expected value resource with uri '" + resourceReference.getValue() + "' but type was '" + value.getClass().getSimpleName() + "'.";
        return failure(message, SHACLM.HasValueConstraintComponent);
      }

      Resource resource = (Resource) value;

      if (Objects.equals(resource.getURI(), resourceReference.getValue())) return new RdfStatements();

      String message = "Value check violated. " +
                       "Expected value resource with uri '" + resourceReference.getValue() + "' but actual uri is '" + resource.getURI() + "'.";

      return failure(message, SHACLM.HasValueConstraintComponent);
    };
  }

  private Function<LanguageIn, RdfStatements> validateLanguageIn() {
    return languageIn -> {
      Objects.requireNonNull(value);

      boolean isLiteral = value instanceof Literal;
      boolean isLanguageLiteral = isLiteral && ((Literal) value).getLanguage() != null;
      if (!isLanguageLiteral) {
        String subMessage = isLiteral ? "Expected a language literal, but type is " + value.getClass().getSimpleName()
                                      : "Expected a language literal, but literal is typed.";
        String message = "Value check violated. " + subMessage;
        return failure(message, SHACLM.LanguageInConstraintComponent);
      }

      String language = ((Literal) value).getLanguage();
      if (languageIn.getValue().contains(language)) return new RdfStatements();

      String message = "Value check violated. " +
                       "Expected value to have language in " + languageIn.getValue() + " but actual language is " + language;
      return failure(message, SHACLM.LanguageInConstraintComponent);
    };
  }

  private RdfStatements failure(String message, Resource constraintComponent) {
    return new ValidationResultBuilder()
            .withMessage(message)
            .withSourceConstraintComponent(constraintComponent).get();
  }

  private void validateRule(Rule rule) {
    RdfStatements rdfStatements = Match(rule).of(
            Case($(instanceOf(Range.class)), r -> validateRange().apply((Range) r)),
            Case($(anyOf(instanceOf(MinCardinality.class),
                    instanceOf(MaxCardinality.class),
                    instanceOf(MinLangCardinality.class),
                    instanceOf(MaxLangCardinality.class))), RdfStatements::new)
    );
    validationResult.add(rdfStatements);
  }

  private Function<Range, RdfStatements> validateRange() {
    return range -> getRangeValidationResult(value, range.getValue(), new HashSet<>());
  }

  private RdfStatements getRangeValidationResult(Object value,
                                                 Rule rule,
                                                 Set<String> ongoingClassValidations) {
    return Match(rule).of(
            Case($(instanceOf(Datatype.class)), r -> validateDatatype().apply((Datatype) r)),
            Case($(instanceOf(Or.class)), r -> validateOr(ongoingClassValidations).apply((Or) r)),
            Case($(instanceOf(Not.class)), r -> validateNot(ongoingClassValidations).apply((Not) r)),
            Case($(instanceOf(And.class)), r -> validateAnd(ongoingClassValidations).apply((And) r)),
            Case($(instanceOf(ClassId.class)), r -> validateClassId(ongoingClassValidations).apply((ClassId) r)),
            Case($(instanceOf(EmbeddedClassId.class)), r -> validateEmbeddedClassId(ongoingClassValidations).apply((EmbeddedClassId) r)),
            Case($(instanceOf(RdfType.class)), r -> validateRdfType().apply((RdfType) r)),
            Case($(instanceOf(InScheme.class)), r -> validateInScheme().apply((InScheme) r)),
            Case($(instanceOf(MemberOf.class)), r -> validateMemberOf().apply((MemberOf) r)),
            Case($(instanceOf(PropertyPath.class)), r -> validatePropertyPath().apply((PropertyPath) r)),
            Case($(instanceOf(LiteralValue.class)), r -> validateLiteralValue().apply((LiteralValue) r)),
            Case($(instanceOf(ResourceReference.class)), r -> validateResourceReference().apply((ResourceReference) r)),
            Case($(instanceOf(LanguageIn.class)), r -> validateLanguageIn().apply((LanguageIn) r)),
            Case($(instanceOf(SubClassOf.class)), r -> validateSubClassOf(ongoingClassValidations).apply((SubClassOf) r))
    );
  }

  private Function<Datatype, RdfStatements> validateDatatype() {
    return datatype -> {
      Objects.requireNonNull(value);

      if (value instanceof Resource && Objects.equals(datatype.getValue(), RDFS.Resource.getURI())) {
        return new RdfStatements();
      }

      if (!(value instanceof Literal)) {
        String message = "Datatype check violated. Expected a literal but type is " + value.getClass().getSimpleName() + ".";

        return failure(message, SHACLM.DatatypeConstraintComponent);
      }

      if (Objects.equals(datatype.getValue(), RDFS.Literal.getURI())) return new RdfStatements();

      Literal literal = (Literal) value;
      RDFDatatype literalDatatype = literal.getDatatype();

      if (!Objects.equals(literalDatatype.getURI(), datatype.getValue())) {
        String message = "Datatype check violated. " +
                         "Expected literal '" + literal.getLexicalForm() + "' to be of datatype '" + datatype.getValue() + "' but found datatype '" + literalDatatype.getURI() + ".";

        return failure(message, SHACLM.DatatypeConstraintComponent);
      }

      return new RdfStatements();
    };
  }
}
