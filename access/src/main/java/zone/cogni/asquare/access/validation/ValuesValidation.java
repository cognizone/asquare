package zone.cogni.asquare.access.validation;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;
import zone.cogni.asquare.access.shacl.Shacl;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MaxLangCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinLangCardinality;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.sem.jena.RdfStatements;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Predicates.instanceOf;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;

public class ValuesValidation {

  public static Function<Rule, RdfStatements> validateRule(@Nonnull TypedResource focusNode,
                                                           @Nonnull ApplicationProfile.Attribute attribute,
                                                           @Nonnull List<?> values) {

    return rule -> Match(rule).of(
            Case($(instanceOf(MinCardinality.class)), min -> validateMinCardinality(focusNode, attribute, values).apply((MinCardinality) min)),
            Case($(instanceOf(MaxCardinality.class)), max -> validateMaxCardinality(focusNode, attribute, values).apply((MaxCardinality) max)),
            Case($(instanceOf(MinLangCardinality.class)), minLang -> validateMinLangCardinality(focusNode, attribute, values).apply((MinLangCardinality) minLang)),
            Case($(instanceOf(MaxLangCardinality.class)), maxLang -> validateMaxLangCardinality(focusNode, attribute, values).apply((MaxLangCardinality) maxLang)),
            Case($(instanceOf(Range.class)), ignore -> new RdfStatements())
    );
  }

  private static Function<MinCardinality, RdfStatements> validateMinCardinality(@Nonnull TypedResource focusNode,
                                                                                @Nonnull ApplicationProfile.Attribute attribute,
                                                                                @Nonnull List<?> values) {
    return minCardinality -> {
      Objects.requireNonNull(minCardinality.getValue(), "Minimum cardinality value is not set.");

      if (values.size() >= minCardinality.getValue()) return new RdfStatements();

      String message = "Min cardinality for attribute '" + attribute.getAttributeId() + "' violated. Limit is " + minCardinality.getValue() + ", count is " + values.size() + ".";

      return new ValidationResultBuilder()
              .withMessage(message)
              .withResultPath(attribute.getUri())
              .withFocusNode(focusNode.getResource())
              .withValue(ResourceFactory.createTypedLiteral(values.size()))
              .withSourceConstraintComponent(Shacl.MinCountConstraintComponent).get();
    };
  }

  private static Function<MaxCardinality, RdfStatements> validateMaxCardinality(@Nonnull TypedResource focusNode,
                                                                                @Nonnull ApplicationProfile.Attribute attribute,
                                                                                @Nonnull List<?> values) {
    return maxCardinality -> {
      Objects.requireNonNull(maxCardinality.getValue(), "Maximum cardinality value is not set.");

      if (values.size() <= maxCardinality.getValue()) return new RdfStatements();

      String message = "Max cardinality for attribute '" + attribute.getAttributeId() + "' violated. Limit is " + maxCardinality.getValue() + ", count is " + values.size() + ".";

      return new ValidationResultBuilder()
              .withMessage(message)
              .withResultPath(attribute.getUri())
              .withFocusNode(focusNode.getResource())
              .withValue(ResourceFactory.createTypedLiteral(values.size()))
              .withSourceConstraintComponent(Shacl.MaxCountConstraintComponent).get();
    };
  }

  private static Function<MaxLangCardinality, RdfStatements> validateMaxLangCardinality(@Nonnull TypedResource focusNode,
                                                                                        @Nonnull ApplicationProfile.Attribute attribute,
                                                                                        @Nonnull List<?> values) {
    return maxLangCardinality -> {
      Objects.requireNonNull(maxLangCardinality.getValue(), "Maximum language cardinality value is not set.");

      Map<String, ? extends List<?>> languageLists = values.stream()
              .collect(Collectors.groupingBy(value -> ((Literal) value).getLanguage()));
      int maxSize = languageLists.entrySet().stream()
              .mapToInt(entry -> entry.getValue().size())
              .max().orElse(0);

      String message = "Max language cardinality for attribute '" + attribute.getAttributeId() + "'  violated. Limit is " + maxLangCardinality.getValue() + ", max is " + maxSize + ".";
      return new ValidationResultBuilder()
              .withMessage(message)
              .withResultPath(attribute.getUri())
              .withFocusNode(focusNode.getResource())
              .withValue(ResourceFactory.createTypedLiteral(values.size()))
              // TODO in SHACL there is no ConstraintComponent. Create our own!
              .get();
    };
  }

  private static Function<MinLangCardinality, RdfStatements> validateMinLangCardinality(@Nonnull TypedResource focusNode,
                                                                                        @Nonnull ApplicationProfile.Attribute attribute,
                                                                                        @Nonnull List<?> values) {
    return minLangCardinality -> {

      Objects.requireNonNull(minLangCardinality.getValue(), "Minimum language cardinality value is not set.");

      Map<String, ? extends List<?>> languageLists = values.stream()
              .collect(Collectors.groupingBy(value -> ((Literal) value).getLanguage()));
      int minSize = languageLists.entrySet().stream()
              .mapToInt(entry -> entry.getValue().size())
              .min().orElse(0);

      String message = "Min language cardinality for attribute '" + attribute.getAttributeId() + "'  violated. Limit is " + minLangCardinality.getValue() + ", min is " + minSize + ".";
      return new ValidationResultBuilder()
              .withMessage(message)
              .withResultPath(attribute.getUri())
              .withFocusNode(focusNode.getResource())
              .withValue(ResourceFactory.createTypedLiteral(values.size()))
              // TODO in SHACL there is no ConstraintComponent. Create our own!
              .get();
    };
  }
}
