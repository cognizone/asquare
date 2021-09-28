package zone.cogni.asquare.access.simplerdf.filter;

import org.apache.jena.vocabulary.SKOS;
import zone.cogni.asquare.access.simplerdf.SparqlFragment;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.EmbeddedClassId;
import zone.cogni.asquare.applicationprofile.rules.InScheme;
import zone.cogni.asquare.applicationprofile.rules.LanguageIn;
import zone.cogni.asquare.applicationprofile.rules.LiteralValue;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MaxLangCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinLangCardinality;
import zone.cogni.asquare.applicationprofile.rules.Or;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.Snippet;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.anyOf;
import static io.vavr.Predicates.instanceOf;


public class AttributeFilter implements Function<ApplicationProfile.Attribute, List<SparqlFragment>> {

  private ApplicationProfile.Attribute attribute;

  @Override
  public List<SparqlFragment> apply(ApplicationProfile.Attribute attribute) {
    this.attribute = attribute;

    return attribute.getRules().stream()
            .flatMap(this::buildSparqlFragment)
            .collect(Collectors.toList());
  }

  private Stream<SparqlFragment> buildSparqlFragment(Rule rule) {
    return Match(rule).of(
            Case($(instanceOf(Range.class)), range2SparqlFragment()),
            Case($(anyOf(instanceOf(MinCardinality.class),
                         instanceOf(MaxCardinality.class),
                         instanceOf(MinLangCardinality.class),
                         instanceOf(MaxLangCardinality.class))), Stream.empty())
    );
  }

  private Function<Range, Stream<SparqlFragment>> range2SparqlFragment() {
    return range -> rangeValue2sparqlFragment(range.getValue());
  }

  private Function<Or, Stream<SparqlFragment>> or2sparqlFragment() {
    return or -> or
            .getValue().stream()
            .flatMap(this::rangeValue2sparqlFragment);
  }

  private Function<And, Stream<SparqlFragment>> and2sparqlFragment() {
    return and -> {
      StringBuilder construct = new StringBuilder();
      StringBuilder filter = new StringBuilder();

      and.getValue().stream()
              .flatMap(this::rangeValue2sparqlFragment)
              .forEach(sparqlFragment -> {
                construct.append("\n\t").append(sparqlFragment.getConstruct());
                filter.append("\n\t").append(sparqlFragment.getFilter());
              });

      return Stream.of(new SparqlFragment("\n\t" + construct,
                                          "\n\t{ " + filter + "\n\t}"));
    };
  }

  private Stream<SparqlFragment> rangeValue2sparqlFragment(Rule rule) {
    return Match(rule).of(
            Case($(instanceOf(Or.class)), or2sparqlFragment()),
            Case($(instanceOf(And.class)), and2sparqlFragment()),
            Case($(instanceOf(ClassId.class)), classId2sparqlFragment()),
            Case($(instanceOf(EmbeddedClassId.class)), embeddedClassId2sparqlFragment()),
            Case($(instanceOf(InScheme.class)), inScheme2sparqlFragment()),
            Case($(anyOf(instanceOf(LiteralValue.class),
                         instanceOf(Datatype.class),
                         instanceOf(LanguageIn.class))), Stream.empty())
    );
  }

  private Function<InScheme, Stream<SparqlFragment>> inScheme2sparqlFragment() {
    return inScheme -> {
      String variable = attribute.getAttributeId();
      String actualScheme = inScheme.getValue();

      String construct = "\t?" + variable + " <" + SKOS.inScheme.getURI() + "> <" + actualScheme + "> .";
      String filter = "\t?" + variable + " <" + SKOS.inScheme.getURI() + "> <" + actualScheme + ">.";
      return Stream.of(new SparqlFragment(construct, filter));

    };
  }

  private Function<ClassId, Stream<SparqlFragment>> classId2sparqlFragment() {
    return classId -> {
      String variable = attribute.getAttributeId();
      String construct = "\t?" + variable + " a ?" + variable + "Type .";
      String filter = "\t?" + variable + " a ?" + variable + "Type .";
      return Stream.of(new SparqlFragment(construct, filter));
    };
  }

  private Function<EmbeddedClassId, Stream<SparqlFragment>> embeddedClassId2sparqlFragment() {
    return embeddedClassId -> {

      ApplicationProfile applicationProfile = attribute.getType().getApplicationProfile();
      ApplicationProfile.Type embeddedType = applicationProfile.getType(embeddedClassId.getValue());

      String typeFilter = TypeFilter.forEmbeddedAttribute(attribute).get();

//      Preconditions.checkState(embeddedType.getRules().size() == 1,
//                               "Currently only an embedded type with a single RdfType rule is supported.");
      RdfType rdfType = embeddedType.getRule(RdfType.class).get();

      String variable = attribute.getAttributeId();
      String construct = "\t?" + variable + " a <" + rdfType.getValue() + "> .";
      String filter = typeFilter;
//              "\t?" + variable + " a <" + rdfType.getValue() + "> .";
      return Stream.of(new SparqlFragment(construct, filter));
    };
  }

  private Function<Snippet, Stream<SparqlFragment>> snippet2sparqlFragment() {
    return snippet -> {
      // todo implement for later if possible ... perhaps with "classId" ?
      return Stream.empty();
    };
  }
}
