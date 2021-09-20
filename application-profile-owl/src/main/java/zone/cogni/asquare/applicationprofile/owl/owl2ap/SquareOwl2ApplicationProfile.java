package zone.cogni.asquare.applicationprofile.owl.owl2ap;

import com.google.common.base.Preconditions;
import io.vavr.control.Option;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.owl.model.rules.Cardinality;
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
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.ResourceReference;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SquareOwl2ApplicationProfile implements Function<Model, ApplicationProfileDef> {

  private static final Logger log = LoggerFactory.getLogger(SquareOwl2ApplicationProfile.class);

  private final PrefixCcService prefixCcService;
  private ApplicationProfileDef applicationProfile;

  public SquareOwl2ApplicationProfile(PrefixCcService prefixCcService) {
    this.prefixCcService = prefixCcService;
  }

  @Override
  public ApplicationProfileDef apply(Model model) {

    this.applicationProfile = new Owl2SquareOwl(prefixCcService).apply(model);

    applicationProfile.getTypeDefs().values().forEach(type -> {
      collapseAttributes()
              .andThen(ApplicationProfileDef.ExtraContainer::clearExtra)
              .accept(type);
    });

    return applicationProfile;
  }

  private Consumer<ApplicationProfileDef.TypeDef> collapseAttributes() {
    return type -> type.getAttributeDefs().values().forEach(this::collapseRules);
  }

  private void collapseRules(ApplicationProfileDef.AttributeDef attribute) {
    collapseCardinality()
            .andThen(splitQualifiedCardinality())
            .andThen(collapseListQualifiedMinCardinalities())
            .andThen(collapseListQualifiedMaxCardinalities())
            .andThen(collapseHasValue())
            .andThen(collapseSomeValuesFrom())
            .andThen(collapseQualifiedMinCardinality())
            .andThen(collapseQualifiedMaxCardinality())
            .andThen(collapseSuperProperty())
            .andThen(removeOwlThingRange())
            .andThen(ApplicationProfileDef.ExtraContainer::clearExtra)
            .accept(attribute);
  }

  private Consumer<ApplicationProfileDef.AttributeDef> collapseQualifiedMinCardinality() {
    return attribute -> {
      Option<MinQualifiedCardinality> startingRule = attribute.getRule(MinQualifiedCardinality.class);
      if (startingRule.isEmpty()) return;

      MinQualifiedCardinality minQualifiedCardinality = startingRule.get();
      updateMinCardinality(attribute, minQualifiedCardinality.getValue());

      updateRange(attribute, minQualifiedCardinality.getQualification());

      // remove minQualifiedCardinality
      attribute.removeRule(minQualifiedCardinality);
    };
  }

  private Consumer<ApplicationProfileDef.AttributeDef> collapseQualifiedMaxCardinality() {
    return attribute -> {
      Option<MaxQualifiedCardinality> startingRule = attribute.getRule(MaxQualifiedCardinality.class);
      if (startingRule.isEmpty()) return;

      MaxQualifiedCardinality maxQualifiedCardinality = startingRule.get();
      updateMaxCardinality(attribute, maxQualifiedCardinality.getValue());

      updateRange(attribute, maxQualifiedCardinality.getQualification());

      // remove maxQualifiedCardinality
      attribute.removeRule(maxQualifiedCardinality);
    };
  }

  private boolean isSubClassOf(ApplicationProfileDef applicationProfile, Rule subClass, Rule superClass) {
    boolean bothClassIdRules = subClass.getRuleName().equals("classId")
                               && superClass.getRuleName().equals("classId");

    if (!bothClassIdRules) return false;

    ClassId subClassId = (ClassId) subClass;
    ClassId superClassId = (ClassId) superClass;


    ApplicationProfileDef.TypeDef subType = applicationProfile.getTypeDef(subClassId.getValue());

    return getSuperClasses(subType).contains(superClassId.getValue());
  }

  private Set<String> getSuperClasses(ApplicationProfileDef.TypeDef type) {
    Set<String> result = new HashSet<>();
    getSuperClasses(type, result);
    return result;
  }

  private void getSuperClasses(ApplicationProfileDef.TypeDef type, Set<String> superClasses) {
    Option<SubClassOf> subClassOfOption = type.getRule(SubClassOf.class);
    if (subClassOfOption.isEmpty()) return;

    SubClassOf subClassOf = subClassOfOption.get();
    superClasses.addAll(subClassOf.getValue());
    subClassOf.getValue()
              .forEach(superClass -> getSuperClasses(applicationProfile.getTypeDef(superClass), superClasses));
  }

  private Consumer<ApplicationProfileDef.AttributeDef> collapseCardinality() {
    return attribute -> {
      attribute.getRule(Cardinality.class)
               .peek(cardinality -> {
                 updateMinCardinality(attribute, cardinality.getValue());
                 updateMaxCardinality(attribute, cardinality.getValue());

                 attribute.removeRule(cardinality);
               });
    };
  }

  private Consumer<ApplicationProfileDef.AttributeDef> splitQualifiedCardinality() {
    return attribute -> {
      attribute.getRule(QualifiedCardinality.class)
               .peek(qualifiedCardinality -> {
                 updateMinQualifiedCardinality(attribute, qualifiedCardinality.getValue(), qualifiedCardinality.getQualification());
                 updateMaxQualifiedCardinality(attribute, qualifiedCardinality.getValue(), qualifiedCardinality.getQualification());

                 removeQualifiedCardinality(attribute, qualifiedCardinality);

               });
    };
  }

  private Consumer<ApplicationProfileDef.AttributeDef> collapseListQualifiedMaxCardinalities() {
    return attribute -> {
      List<MaxQualifiedCardinality> maxQualifiedCardinalities = findRules(attribute, MaxQualifiedCardinality.class);
      if (maxQualifiedCardinalities.isEmpty()) return;

      Preconditions.checkState(maxQualifiedCardinalities.size() <= 1);
      MaxQualifiedCardinality maxQualifiedCardinality = maxQualifiedCardinalities.get(0);

      List<MaxCardinality> maxCardinalities = findRules(attribute, MaxCardinality.class);
      Preconditions.checkState(maxCardinalities.size() <= 1);

      List<Range> ranges = findRules(attribute, Range.class);
      Preconditions.checkState(ranges.size() <= 1);

      if (ranges.isEmpty()) {
        // add range
        updateMaxCardinality(attribute, maxQualifiedCardinality.getValue());
        attribute.addRule(new Range(maxQualifiedCardinality.getQualification()));
        attribute.removeRule(maxQualifiedCardinality);
      }
      else {
        Range range = ranges.get(0);
        if (Objects.equals(range.getValue(), maxQualifiedCardinality.getQualification())) {
          // qualified can be removed
          updateMaxCardinality(attribute, maxQualifiedCardinality.getValue());
          attribute.removeRule(maxQualifiedCardinality);
        }
        // else to big a difference
      }
    };
  }


  private Consumer<ApplicationProfileDef.AttributeDef> collapseListQualifiedMinCardinalities() {
    return attribute -> {
      List<MinQualifiedCardinality> minQualifiedCardinalities = findRules(attribute, MinQualifiedCardinality.class);
      if (minQualifiedCardinalities.isEmpty()) return;

      Preconditions.checkState(minQualifiedCardinalities.size() <= 1);
      MinQualifiedCardinality minQualifiedCardinality = minQualifiedCardinalities.get(0);

      List<MinCardinality> minCardinalities = findRules(attribute, MinCardinality.class);
      Preconditions.checkState(minCardinalities.size() <= 1);

      List<Range> ranges = findRules(attribute, Range.class);
      Preconditions.checkState(ranges.size() <= 1);

      if (ranges.isEmpty()) {
        // add range
        attribute.addRule(new Range(minQualifiedCardinality.getQualification()));
        updateMinCardinality(attribute, minQualifiedCardinality.getValue());

        attribute.removeRule(minQualifiedCardinality);
      }
      else {
        Range range = ranges.get(0);
        if (Objects.equals(range.getValue(), minQualifiedCardinality.getQualification())) {
          // qualified can be removed
          updateMinCardinality(attribute, minQualifiedCardinality.getValue());
          attribute.removeRule(minQualifiedCardinality);
        }
        // else to big a difference
      }
    };
  }

  private void updateMinQualifiedCardinality(ApplicationProfileDef.AttributeDef attribute, Integer value, Rule qualification) {
    MinQualifiedCardinality minCardinality = findRule(attribute, MinQualifiedCardinality.class)
            .getOrElse(() -> add(attribute, new MinQualifiedCardinality(value, qualification)));

    if (Objects.equals(qualification, minCardinality.getQualification())) {
      minCardinality.setValue(Math.max(minCardinality.getValue(), value));
    }
    else {
      log.error("Problem with merging MinQualifiedCardinality and QualifiedCardinality on attribute '{}'. " +
                "Qualifications do not match: {} and {}.",
                attribute.getAttributeId(), qualification, minCardinality.getQualification());
    }

    if (minCardinality.getValue() == 0) {
      attribute.removeRule(minCardinality);
    }
  }


  /**
   * hasValue can be a range with a specific resource reference.
   */
  private Consumer<ApplicationProfileDef.AttributeDef> collapseHasValue() {
    return attribute -> {
      List<HasValue> hasValues = findRules(attribute, HasValue.class);
      if (hasValues.isEmpty()) return;

      Preconditions.checkState(hasValues.size() <= 1);
      HasValue hasValue = hasValues.get(0);

      findRule(attribute, Range.class)
              .peek(range -> {
                ResourceReference resourceReference = new ResourceReference(hasValue.getValue());

                List<Rule> rules = Arrays.asList(range.getValue(), resourceReference);

                range.setValue(new And(rules));
                attribute.removeRule(hasValue);
              })
              .onEmpty(() -> {
                attribute.addRule(new Range(new ResourceReference(hasValue.getValue())));
                attribute.removeRule(hasValue);
              });
    };
  }

  /**
   * someValuesFrom can be collapsed if attribute has a range that matches range of someValueFrom.
   * In that case the min cardinality is also at least one!
   */
  private Consumer<ApplicationProfileDef.AttributeDef> collapseSomeValuesFrom() {
    return attribute -> {
      List<SomeValuesFrom> someValuesFromList = findRules(attribute, SomeValuesFrom.class);
      if (someValuesFromList.isEmpty()) return;

      Preconditions.checkState(someValuesFromList.size() <= 1);
      SomeValuesFrom someValuesFrom = someValuesFromList.get(0);

      List<Range> rangeList = findRules(attribute, Range.class);
      if (rangeList.size() != 1) return;

      Range range = rangeList.get(0);

      System.out.println("range = " + range.getValue());
      System.out.println("someValuesFrom = " + someValuesFrom.getValue());
      if (!Objects.equals(range.getValue(), someValuesFrom.getValue())) return;

      findRule(attribute, MinCardinality.class)
              .peek(minCardinality -> {
                minCardinality.setValue(Math.max(minCardinality.getValue(), 1));
              })
              .onEmpty(() -> {
                attribute.addRule(new MinCardinality(1));
              });

      attribute.removeRule(someValuesFrom);
    };
  }

  private Consumer<ApplicationProfileDef.AttributeDef> collapseSuperProperty() {
    return attribute -> {
      if (attribute.getRule(SubPropertyOf.class).isEmpty()) return;

      SubPropertyOf subPropertyOf = attribute.getRule(SubPropertyOf.class).get();
      List<String> superPropertyUris = subPropertyOf.getValue();

      mergeSuperProperties(attribute, superPropertyUris);

      attribute.removeRule(subPropertyOf);
    };
  }

  private void mergeSuperProperties(ApplicationProfileDef.AttributeDef attribute, List<String> subPropertyUris) {
    getAllAttributes()
            .filter(attr -> subPropertyUris.contains(attr.getUri()))
            .forEach(superAttribute -> {
              superAttribute.getRules()
                            .forEach(rule -> enhanceAttributeWithSuperPropertyRule(attribute, superAttribute, rule));
            });
  }

  private Stream<ApplicationProfileDef.AttributeDef> getAllAttributes() {
    return applicationProfile.getTypeDefs().values().stream()
                             .flatMap(type -> type.getAttributeDefs().values().stream());
  }

  private void enhanceAttributeWithSuperPropertyRule(ApplicationProfileDef.AttributeDef attribute,
                                                     ApplicationProfileDef.AttributeDef superAttribute,
                                                     Rule rule) {
    if (rule instanceof Cardinality) {
      // TODO better logging!
      log.warn("{}.{} inherited min / max cardinality from {}.");
      updateMinCardinality(attribute, ((Cardinality) rule).getValue());
      updateMaxCardinality(attribute, ((Cardinality) rule).getValue());
      return;
    }
    if (rule instanceof MinCardinality) {
      updateMinCardinality(attribute, ((MinCardinality) rule).getValue());
      return;
    }
    if (rule instanceof MaxCardinality) {
      updateMaxCardinality(attribute, ((MaxCardinality) rule).getValue());
      return;
    }
    if (rule instanceof MinQualifiedCardinality) {
      log.error("Ignoring MinQualifiedCardinality {}", rule);
      // TODO
      return;
    }
    if (rule instanceof SubPropertyOf) {
      List<String> superPropertyUris = superAttribute.getRule(SubPropertyOf.class).get().getValue();
      mergeSuperProperties(attribute, superPropertyUris);
      return;
    }
    if (rule instanceof Range) {
      updateRange(attribute, ((Range) rule).getValue());
      return;
    }
    if (rule instanceof InverseOf) {
      log.error("Ignoring InverseOf {}", rule);
      // TODO ignore for now
      return;
    }

    throw new RuntimeException("Unhandled rule for super property " + superAttribute.getAttributeId()
                               + " on " + attribute.getTypeDef().getClassId() + "." + attribute.getAttributeId()
                               + ": " + rule);
  }

  private Consumer<ApplicationProfileDef.AttributeDef> removeOwlThingRange() {
    return attribute -> {
      if (attribute.getRule(Range.class).isEmpty()) return;
      Range range = attribute.getRule(Range.class).get();


      if (range.getValue() instanceof And) {
        // TODO finish => note: we might have a problem with the names here!!!
        ((And) range.getValue()).getValue();
      }
    };
  }

  private void updateRange(ApplicationProfileDef.AttributeDef attribute, Rule newRangeRule) {
    Option<Range> rangeOption = findRule(attribute, Range.class);

    if (attribute.getAttributeId().equals("transpositionDraft")) {
      System.out.println("rangeOption = " + rangeOption);
    }

    if (rangeOption.isEmpty()) {
      attribute.addRule(new Range(newRangeRule));
      return;
    }

    Range range = rangeOption.get();
    Rule oldRangeRule = range.getValue();

    boolean sameRange = oldRangeRule.equals(newRangeRule);
    if (sameRange) return;

    boolean newRangeRuleIsSubClass = isSubClassOf(applicationProfile, newRangeRule, oldRangeRule);
    if (newRangeRuleIsSubClass) {
      ((ClassId) oldRangeRule).setValue(((ClassId) newRangeRule).getValue());
      return;
    }

    boolean oldRangeRuleIsSubClass = isSubClassOf(applicationProfile, oldRangeRule, newRangeRule);
    if (oldRangeRuleIsSubClass) {
      log.info("Update {}.{} of range with a more generic class does not have effect: old {} and new {}.",
               attribute.getTypeDef().getClassId(),
               attribute.getAttributeId(),
               ((ClassId) oldRangeRule).getValue(),
               ((ClassId) newRangeRule).getValue());
      return;
    }

    log.warn("Update {}.{} of range. Merging two ranges!",
             attribute.getTypeDef().getClassId(),
             attribute.getAttributeId());

    boolean isAndRule = oldRangeRule instanceof And;
    if (isAndRule) {
      // append new to list in and
      ((And) oldRangeRule).getValue().add(newRangeRule);
    }
    else {
      range.setValue(new And(Stream.of(oldRangeRule, newRangeRule)
                                   .collect(Collectors.toList()))
                    );
    }
  }

  private void updateMinCardinality(ApplicationProfileDef.AttributeDef attribute, Integer value) {
    MinCardinality defaultCardinality = new MinCardinality(0);

    MinCardinality cardinality = findRule(attribute, MinCardinality.class)
            .getOrElse(() -> add(attribute, defaultCardinality));
    cardinality.setValue(Math.max(cardinality.getValue(), value));

    if (Objects.equals(cardinality.getValue(), 0)) {
      attribute.removeRule(cardinality);
    }
  }

  private <T extends Rule> T add(ApplicationProfileDef.AttributeDef attribute, T rule) {
    attribute.addRule(rule);
    return rule;
  }

  private void updateMaxCardinality(ApplicationProfileDef.AttributeDef attribute, Integer value) {
    MaxCardinality defaultCardinality = new MaxCardinality(Integer.MAX_VALUE);

    MaxCardinality cardinality = findRule(attribute, MaxCardinality.class)
            .getOrElse(() -> add(attribute, defaultCardinality));
    cardinality.setValue(Math.min(cardinality.getValue(), value));

    if (Objects.equals(cardinality.getValue(), Integer.MAX_VALUE)) {
      attribute.removeRule(cardinality);
    }
  }

  private void updateMaxQualifiedCardinality(ApplicationProfileDef.AttributeDef attribute, Integer value, Rule qualification) {
    MaxQualifiedCardinality maxCardinality = findRule(attribute, MaxQualifiedCardinality.class)
            .getOrElse(() -> add(attribute, new MaxQualifiedCardinality(value, qualification)));

    if (Objects.equals(qualification, maxCardinality.getQualification())) {
      maxCardinality.setValue(Math.min(maxCardinality.getValue(), value));
    }
    else {
      log.error("Problem with merging MaxQualifiedCardinality and QualifiedCardinality on attribute '{}'. " +
                "Qualifications do not match: {} and {}.",
                attribute.getAttributeId(), qualification, maxCardinality.getQualification());
    }

    if (maxCardinality.getValue() == Integer.MAX_VALUE) {
      attribute.removeRule(maxCardinality);
    }
  }

  private void removeQualifiedCardinality(ApplicationProfileDef.AttributeDef attribute, QualifiedCardinality qualifiedCardinality) {
    Option<Boolean> minQualificationMatch = attribute.getRule(MinQualifiedCardinality.class)
                                                     .map(minQualifiedCardinality -> Objects.equals(minQualifiedCardinality.getQualification(),
                                                                                                    qualifiedCardinality.getQualification()));

    Option<Boolean> maxQualificationMatch = attribute.getRule(MaxQualifiedCardinality.class)
                                                     .map(maxQualifiedCardinality -> Objects.equals(maxQualifiedCardinality.getQualification(),
                                                                                                    qualifiedCardinality.getQualification()));

    if (minQualificationMatch.getOrElse(false) || maxQualificationMatch.getOrElse(false)) {
      attribute.removeRule(qualifiedCardinality);
    }
  }

  private <T extends Rule> List<T> findRules(ApplicationProfileDef.AttributeDef attribute, Class<T> type) {
    Stream<Rule> ruleStream = attribute.getRules().stream()
                                       .filter(rule -> Objects.equals(rule.getClass(), type));
    return (List<T>) ruleStream.collect(Collectors.toList());
  }

  private <T extends Rule> Option<T> findRule(ApplicationProfileDef.AttributeDef attribute, Class<T> type) {
    Optional<Rule> foundRule = attribute.getRules().stream()
                                        .filter(rule -> Objects.equals(rule.getClass(), type))
                                        .findFirst();
    return (Option<T>) Option.ofOptional(foundRule);
  }

}
