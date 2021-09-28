package zone.cogni.asquare.applicationprofile.model.basic;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.RdfTypes;
import zone.cogni.asquare.applicationprofile.rules.Snippet;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.API.run;
import static java.util.function.Predicate.isEqual;

public class CollapseApplicationProfile implements Function<ApplicationProfile, ApplicationProfile> {

  public Logger log = LoggerFactory.getLogger(CollapseApplicationProfile.class);

  @Override
  public ApplicationProfile apply(ApplicationProfile original) {
    ApplicationProfileDef originalBasicApplicationProfile = original.getApplicationProfileDef().getRootDefinition();

    List<ApplicationProfileDef> profiles = getApplicationProfiles(originalBasicApplicationProfile);

    ApplicationProfileDef applicationProfile = ApplicationProfileDef.newInstance();
    applicationProfile.setUri(originalBasicApplicationProfile.getUri());
    addApplicationProfileType(applicationProfile, profiles);
    addApplicationProfileExtra(applicationProfile, profiles);

    return new BasicApplicationProfile(applicationProfile);
  }

  private void addApplicationProfileType(ApplicationProfileDef applicationProfile, List<ApplicationProfileDef> profiles) {
    getTypeIds(profiles).forEach(typeId -> {
      applicationProfile.addTypeDef(getType(profiles, typeId));
    });
  }

  private void addApplicationProfileExtra(ApplicationProfileDef applicationProfile, List<ApplicationProfileDef> profiles) {
    profiles.forEach(profile -> {
      if (profile.getExtra() == null) return;
      Preconditions.checkState(profile.getExtra().getValue().isEmpty(),
                               "Application profile with extra is not supported yet.");
    });
  }

  private List<ApplicationProfileDef> getApplicationProfiles(ApplicationProfileDef applicationProfile) {
    return applicationProfile.getApplicationProfileDefs().stream()
                             .map(ApplicationProfileDef::getRootDefinition)
                             .collect(Collectors.toList());
  }

  private Set<String> getTypeIds(List<ApplicationProfileDef> applicationProfiles) {
    return applicationProfiles.stream()
                              .flatMap(ap -> ap.getTypeDefs().keySet().stream())
                              .collect(Collectors.toSet());

  }

  private ApplicationProfileDef.TypeDef getType(List<ApplicationProfileDef> profiles, String typeId) {
    Set<ApplicationProfileDef.TypeDef> types = getTypes(profiles, typeId);

    ApplicationProfileDef.TypeDef result = ApplicationProfileDef.TypeDef.newInstance();
    result.setClassId(typeId);
    addTypeExtra(result, types);
    addTypeRules(profiles, result, types);
    addTypeAttributes(profiles, result, getAllSuperTypes(profiles, types));

    return result;
  }

  private void addTypeExtra(ApplicationProfileDef.TypeDef type, Set<ApplicationProfileDef.TypeDef> types) {
    types.forEach(t -> {
      if (t.getExtra() == null) return;
      else {
        boolean isOnlyComment = t.getExtra()
                                 .getValue()
                                 .stream()
                                 .allMatch(propertyValue -> propertyValue.getProperty().equals("rdfsComment"));
        if (!isOnlyComment)
          throw new IllegalStateException("Type '" + type.getClassId() + "' contains unsupported extra.");
      }
    });
  }

  private void addTypeRules(List<ApplicationProfileDef> profiles, ApplicationProfileDef.TypeDef type, Set<ApplicationProfileDef.TypeDef> types) {
    getAllSuperTypes(profiles, types).stream()
                                     .flatMap(t -> t.getRules().stream())
                                     .forEach(rule -> addRule(type, rule));
  }

  private Stream<ApplicationProfileDef.TypeDef> getSuperTypes(List<ApplicationProfileDef> profiles, ApplicationProfileDef.TypeDef type) {
    return type.getRules(SubClassOf.class).stream()
               .flatMap(subClassOf -> subClassOf.getValue().stream())
               .distinct()
               .flatMap(typeId -> getTypes(profiles, typeId).stream());
  }

  private void getSuperTypes(List<ApplicationProfileDef> profiles, ApplicationProfileDef.TypeDef type, Set<ApplicationProfileDef.TypeDef> superTypes) {
    superTypes.add(type);
    getSuperTypes(profiles, type).forEach(t -> getSuperTypes(profiles, t, superTypes));
  }


  private Set<ApplicationProfileDef.TypeDef> getAllSuperTypes(List<ApplicationProfileDef> profiles, Set<ApplicationProfileDef.TypeDef> types) {
    Set<ApplicationProfileDef.TypeDef> superTypes = new HashSet<>();
    types.forEach(t -> getSuperTypes(profiles, t, superTypes));
    return superTypes;
  }

  private void addTypeAttributes(List<ApplicationProfileDef> profiles, ApplicationProfileDef.TypeDef type, Set<ApplicationProfileDef.TypeDef> types) {
    getAttributeIds(profiles, types).forEach(attributeId -> {
      Set<ApplicationProfileDef.AttributeDef> attributes = getAttributes(types, attributeId);
      type.addAttributeDef(getAttribute(attributeId, attributes));
    });
  }

  private Set<ApplicationProfileDef.AttributeDef> getAttributes(Set<ApplicationProfileDef.TypeDef> types, String attributeId) {
    return types.stream()
                .filter(type -> type.hasAttributeDef(attributeId))
                .map(type -> type.getAttributeDef(attributeId))
                .collect(Collectors.toSet());
  }

  private ApplicationProfileDef.AttributeDef getAttribute(String attributeId, Set<ApplicationProfileDef.AttributeDef> attributes) {
    ApplicationProfileDef.AttributeDef result = ApplicationProfileDef.AttributeDef.newInstance();
    result.setAttributeId(attributeId);
    addAttributeUri(result, attributes);
    addAttributeExtra(result, attributes);
    addAttributeRules(result, attributes);
    return result;
  }

  private void addAttributeUri(ApplicationProfileDef.AttributeDef attribute, Set<ApplicationProfileDef.AttributeDef> attributes) {
    boolean hasOneUri = attributes.stream()
                                  .map(ApplicationProfileDef.AttributeDef::getUri)
                                  .distinct().count() == 1;
    Preconditions.checkState(hasOneUri, "Attribute " + attribute.getAttributeId() + " can only have one unique URI.");

    String uri = attributes.stream().findFirst().get().getUri();
    attribute.setUri(uri);
  }

  private void addAttributeExtra(ApplicationProfileDef.AttributeDef attribute, Set<ApplicationProfileDef.AttributeDef> attributes) {
    attributes.forEach(attr -> {
      if (attr.getExtra() == null) return;

      boolean isOnlyComment = attr.getExtra()
                                  .getValue()
                                  .stream()
                                  .allMatch(propertyValue -> propertyValue.getProperty().equals("rdfsComment"));
      if (!isOnlyComment)
        throw new IllegalStateException("Type '" + attribute.getAttributeId() + "' contains unsupported extra.");
    });
  }

  private void addAttributeRules(ApplicationProfileDef.AttributeDef attribute, Set<ApplicationProfileDef.AttributeDef> attributes) {
    attributes.stream()
              .flatMap(attr -> attr.getRules().stream())
              .forEach(rule -> addRule(attribute, rule));
  }

  private void addRule(ApplicationProfileDef.RuleContainer ruleContainer, Rule<?> rule) {
    Match(rule.getClass()).of(
            Case($(isEqual(MinCardinality.class)),
                 o -> run(() -> addMinCardinality((ApplicationProfileDef.AttributeDef) ruleContainer, (MinCardinality) rule))),
            Case($(isEqual(MaxCardinality.class)),
                 o -> run(() -> addMaxCardinality((ApplicationProfileDef.AttributeDef) ruleContainer, (MaxCardinality) rule))),
            Case($(isEqual(RdfType.class)),
                 o -> run(() -> addRdfType((ApplicationProfileDef.TypeDef) ruleContainer, (RdfType) rule))),
            Case($(isEqual(Range.class)),
                 o -> run(() -> addRange((ApplicationProfileDef.AttributeDef) ruleContainer, (Range) rule))),
            Case($(isEqual(Snippet.class)),
                 o -> run(() -> addSnippet((ApplicationProfileDef.AttributeDef) ruleContainer, (Snippet) rule))),

            // SubClassOf is ignored !
            Case($(isEqual(SubClassOf.class)), o -> run(this::doNothing))
                             );
  }

  private void doNothing() {
    // nothing yet
  }

  private <R1 extends Rule<R1>, R2 extends Rule<R2>> void updateAttribute(@Nonnull ApplicationProfileDef.RuleContainer ruleContainer,
                                                                          @Nullable R1 oldRule,
                                                                          @Nonnull R2 newRule) {
    if (oldRule != null) ruleContainer.removeRule(oldRule);
    ruleContainer.addRule(newRule.copy());
  }

  private void addMinCardinality(@Nonnull ApplicationProfileDef.AttributeDef attribute,
                                 @Nonnull MinCardinality candidate) {
    MinCardinality oldRule = attribute.getRule(MinCardinality.class).getOrNull();
    MinCardinality newRule = newMinCardinality(oldRule, candidate);

    updateAttribute(attribute, oldRule, newRule);
  }

  private MinCardinality newMinCardinality(@Nullable MinCardinality current,
                                           @Nonnull MinCardinality candidate) {
    return current == null ? candidate
                           : current.getValue() > candidate.getValue() ? current
                                                                       : candidate;
  }

  private void addMaxCardinality(@Nonnull ApplicationProfileDef.AttributeDef attribute,
                                 @Nonnull MaxCardinality candidate) {
    MaxCardinality oldRule = attribute.getRule(MaxCardinality.class).getOrNull();
    MaxCardinality newRule = newMaxCardinality(oldRule, candidate);

    updateAttribute(attribute, oldRule, newRule);
  }

  private MaxCardinality newMaxCardinality(@Nullable MaxCardinality current,
                                           @Nonnull MaxCardinality candidate) {
    return current == null || current.getValue() > candidate.getValue()
           ? candidate
           : current;
  }

  private void addRdfType(@Nonnull ApplicationProfileDef.TypeDef ruleContainer,
                          @Nonnull RdfType candidate) {

    RdfType oldRdfType = ruleContainer.getRule(RdfType.class).getOrNull();
    RdfTypes oldRdfTypes = ruleContainer.getRule(RdfTypes.class).getOrNull();

    Rule oldRule = oldRdfType != null ? oldRdfType : oldRdfTypes;
    RdfTypes newRule = oldRdfType != null ? newRdfTypes(oldRdfType, candidate)
                                          : newRdfTypes(oldRdfTypes, candidate);

    updateAttribute(ruleContainer, oldRule, newRule);
  }

  private RdfTypes newRdfTypes(@Nullable RdfType current,
                               @Nonnull RdfType candidate) {
    return new RdfTypes(getRdfTypeNames(current, candidate));
  }

  private List<String> getRdfTypeNames(@Nullable RdfType one,
                                       @Nonnull RdfType other) {
    List<String> names = new ArrayList<>();
    if (one != null) {
      names.add(one.getValue());
    }
    names.add(other.getValue());
    return names;
  }


  private RdfTypes newRdfTypes(@Nullable RdfTypes current,
                               @Nonnull RdfType candidate) {
    return new RdfTypes(getRdfTypeNames(current, candidate));
  }

  private List<String> getRdfTypeNames(@Nullable RdfTypes one,
                                       @Nonnull RdfType other) {
    List<String> names = new ArrayList<>();
    if (one != null) {
      names.addAll(one.getValue());
    }
    names.add(other.getValue());
    return names;
  }

  private void addRange(@Nonnull ApplicationProfileDef.AttributeDef ruleContainer,
                        @Nonnull Range candidate) {
    Range oldRule = ruleContainer.getRule(Range.class).getOrNull();
    Range newRule = newRangeRule(oldRule, candidate);

    updateAttribute(ruleContainer, oldRule, newRule);
  }

  private Range newRangeRule(@Nullable Range oldRule,
                             @Nonnull Range candidate) {
    if (oldRule == null) return candidate;

    return new Range(new And(Stream.of(oldRule.getValue(),
                                       candidate.getValue()).collect(Collectors.toList())
    ));
  }

  private void addSnippet(@Nonnull ApplicationProfileDef.AttributeDef ruleContainer,
                          @Nonnull Snippet candidate) {
    Snippet oldRule = ruleContainer.getRule(Snippet.class).getOrNull();
    Preconditions.checkState(oldRule == null);
    ruleContainer.addRule(candidate);
  }

  private Set<ApplicationProfileDef.TypeDef> getTypes(Collection<ApplicationProfileDef> profiles, String typeId) {
    return profiles.stream()
                   .filter(profile -> profile.hasTypeDef(typeId))
                   .map(profile -> profile.getTypeDef(typeId))
                   .collect(Collectors.toSet());
  }

  private Set<String> getAttributeIds(List<ApplicationProfileDef> profiles, Set<ApplicationProfileDef.TypeDef> types) {
    return getAllSuperTypes(profiles, types).stream()
                                            .flatMap(type -> type.getAttributeIds().stream())
                                            .collect(Collectors.toSet());
  }

}
