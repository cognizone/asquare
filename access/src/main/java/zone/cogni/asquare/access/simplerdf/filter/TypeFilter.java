package zone.cogni.asquare.access.simplerdf.filter;

import com.google.common.base.Preconditions;
import io.vavr.Predicates;
import io.vavr.control.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.EmbeddedClassId;
import zone.cogni.asquare.applicationprofile.rules.InScheme;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.ResourceReference;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;

/**
 * Creates SPARQL block for filtering of a AP Type.
 */
public class TypeFilter implements Supplier<String> {

  public static TypeFilter forType(ApplicationProfile.Type type) {
    return new TypeFilter(new TypeContext(type));
  }

  public static TypeFilter forEmbeddedAttribute(ApplicationProfile.Attribute attribute) {
    return new TypeFilter(new EmbeddedAttributeContext(attribute));
  }

  public static Option<ApplicationProfile.Type> getEmbeddedTypeFor(ApplicationProfile.Attribute attribute) {
    Option<Range> rangeOption = attribute.getRule(Range.class);
    if (rangeOption.isEmpty()) return Option.none();

    Range range = rangeOption.get();
    if (!(range.getValue() instanceof EmbeddedClassId)) return Option.none();

    EmbeddedClassId embeddedClassId = (EmbeddedClassId) range.getValue();
    ApplicationProfile applicationProfile = attribute.getType().getApplicationProfile();
    ApplicationProfile.Type type = applicationProfile.getType(embeddedClassId.getValue());
    return Option.of(type);
  }

  private final Context context;

  private TypeFilter(Context context) {
    this.context = context;
  }

  @Override
  public String get() {
    return buildFilter(context.getType(),
                       new And(context.getType().getRules()));
  }

  // INFO: some logic is copied from GetGraphPatternClassExtensionVisitor
  private String buildFilter(ApplicationProfile.Type type, Rule rule) {
    return Match(rule).of(
            Case($(Predicates.instanceOf(And.class)), and2Sparql(type)),
            Case($(Predicates.instanceOf(RdfType.class)), rdfType2Sparql()),
            Case($(Predicates.instanceOf(ResourceReference.class)), resourceReference2Sparql()),
            Case($(Predicates.instanceOf(InScheme.class)), inScheme2Sparql()),

            // TODO should disappear if we make Type "smarter"
            Case($(Predicates.instanceOf(SubClassOf.class)), subClassOf2Sparql(type))
//            Case($(Predicates.instanceOf(Not.class)), getNot2FragmentFunction()),
//            Case($(Predicates.instanceOf(MemberOf.class)), getMemberOf2FragmentFunction()),
//            Case($(Predicates.instanceOf(PropertyPath.class)), getPropertyPath2FragmentFunction()),
    );
  }

  // TODO should disappear if we make Type "smarter"
  private Function<SubClassOf, String> subClassOf2Sparql(ApplicationProfile.Type type) {
    return subClassOf -> {
      ApplicationProfile applicationProfile = type.getApplicationProfile();

      List<String> classIds = subClassOf.getValue();
      Preconditions.checkState(classIds.stream().allMatch(applicationProfile::hasType));

      return classIds.stream()
              .map(applicationProfile::getType)
              .map(superType -> getSuperClassFilter(type, superType))
              .filter(StringUtils::isNotBlank)
              .collect(Collectors.joining(". \n\t\t", "\n\t { ", " }"));
    };
  }

  private String getSuperClassFilter(ApplicationProfile.Type type, ApplicationProfile.Type superType) {
    Stream<Rule> superClassRules = type.getRules().stream()
            .filter(rule -> !Objects.equals(rule.getClass(), RdfType.class));

    And and = new And(superClassRules.collect(Collectors.toList()));
    return buildFilter(superType, and);
  }

  private Function<And, String> and2Sparql(ApplicationProfile.Type type) {
    return and -> and
            .getValue().stream()
            .map(rule -> buildFilter(type, rule))
            .collect(Collectors.joining(". \n\t\t", "\n\t { ", " }"));
  }

  private Function<RdfType, String> rdfType2Sparql() {
    return rdfType -> context.getVariable() + " <" + RDF.type.getURI() + "> <" + rdfType.getValue() + ">";
  }

  private Function<ResourceReference, String> resourceReference2Sparql() {
    return resourceReference -> "FILTER (" + context.getVariable() + " = <" + resourceReference.getValue() + ">)";
  }

  private Function<InScheme, String> inScheme2Sparql() {
    return inScheme -> context.getVariable() + " <" + SKOS.inScheme.getURI() + "> <" + inScheme.getValue() + ">";
  }

  private abstract static class Context {

    abstract String getVariable();

    abstract ApplicationProfile.Type getType();

  }

  private static class EmbeddedAttributeContext extends Context {

    private final ApplicationProfile.Attribute attribute;

    private EmbeddedAttributeContext(ApplicationProfile.Attribute attribute) {
      this.attribute = attribute;
    }

    @Override
    String getVariable() {
      return "?" + attribute.getAttributeId();
    }

    @Override
    ApplicationProfile.Type getType() {
      return getEmbeddedTypeFor(attribute).get();
    }

  }

  private static class TypeContext extends Context {

    private final ApplicationProfile.Type type;

    private TypeContext(ApplicationProfile.Type type) {
      this.type = type;
    }

    @Override
    String getVariable() {
      return "?resource";
    }

    @Override
    ApplicationProfile.Type getType() {
      return type;
    }
  }

}
