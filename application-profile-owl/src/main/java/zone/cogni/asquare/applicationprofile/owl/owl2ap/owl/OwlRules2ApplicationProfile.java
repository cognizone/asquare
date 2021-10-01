package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl;

import com.google.common.collect.Sets;
import io.vavr.Tuple2;
import org.apache.jena.ext.com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.applicationprofile.Constants;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef.AttributeDef;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef.TypeDef;
import zone.cogni.asquare.applicationprofile.model.basic.def.BasicAttributeDef;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.DatatypePropertyReference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.ObjectPropertyReference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlCardinality;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlClassReference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.OwlClassReferenceCandidate;
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
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.Reference;
import zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules.SomeValuesFrom;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MinCardinality;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.SubClassOf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class OwlRules2ApplicationProfile implements Function<OwlRules, ApplicationProfileDef> {

  private static final Logger log = LoggerFactory.getLogger(OwlRules2ApplicationProfile.class);

  private OwlRules owlRules;
  private ApplicationProfileDef applicationProfile;
  private Map<String, IntermediateAttribute> intermediateAttributeMap;

  @Override
  public ApplicationProfileDef apply(OwlRules owlRules) {
    init(owlRules);
    process();

    return applicationProfile;
  }

  /**
   * Init is needed to ensure all is set up correctly when being reused.
   */
  private void init(OwlRules owlRules) {
    this.owlRules = owlRules;

    applicationProfile = ApplicationProfileDef.newInstance();
    intermediateAttributeMap = new HashMap<>();
  }

  private void process() {

    calculateExtraClassProperties();
    calculateExtraPropertyProperties();

    processOntology();
    processClasses();
    processSubClassOf();
    processProperties();
    processRange();
    processDomain();
    processCardinality();

    owlRules.getExactRules(SomeValuesFrom.class)
            .forEach(System.out::println);

    System.out.println("hello");
  }


  private void calculateExtraClassProperties() {
    List<OwlExtra> extraRules = getExtraRules(true);
    List<String> result = getPropertiesOfExtraRules(extraRules);

    log.info("Extra class properties: {}", result.size());
    log.info("    {}", result);

    owlRules.remove("Cleanup extra class properties", extraRules);
  }

  private void calculateExtraPropertyProperties() {
    List<OwlExtra> extraRules = getExtraRules(false);
    List<String> result = getPropertiesOfExtraRules(extraRules);

    log.info("Extra property properties: {}", result.size());
    log.info("    {}", result);

    owlRules.remove("Cleanup extra attribute properties", extraRules);
  }

  private void processOntology() {
    List<OwlOntology> ontologyList = owlRules.getExactRules(OwlOntology.class);
    Preconditions.checkState(ontologyList.size() == 1);

    OwlOntology ontology = ontologyList.get(0);
    applicationProfile.setUri(ontology.getValue().getValue());

    owlRules.remove("Ontology", ontology);
  }

  private void processClasses() {
    owlRules.getAssignableRules((Class<? extends Reference>) OwlClassReference.class)
            .forEach(classReference -> {
              ensureTypeExists(classReference);
              owlRules.remove("New class", classReference);
            });
  }

  private void ensureTypeExists(Reference classReference) {
    removeClassReferenceCandidate(classReference);

    if (applicationProfile.hasTypeDef(classReference.getValue())) {
      return;
    }

    TypeDef type = TypeDef.newInstance();
    type.setApplicationProfileDef(applicationProfile);
    type.setClassId(classReference.getValue());

    applicationProfile.addTypeDef(type);
  }

  private void removeClassReferenceCandidate(Reference classReference) {
    List<OwlClassReferenceCandidate> candidates =
            owlRules.getExactRules(OwlClassReferenceCandidate.class)
                    .stream()
                    .filter(candidate -> candidate.getValue().equals(classReference.getValue()))
                    .collect(Collectors.toList());

    if (candidates.isEmpty()) return;

    log.info("Removing ClassReferenceCandidates {}: {}", classReference.getValue(), candidates.size());
    owlRules.remove("Cleanup ClassReferenceCandidate", candidates);
  }

  private void processSubClassOf() {
    owlRules.getExactRules(OwlSubClassOf.class)
            .stream()
            // group by class which has superclasses
            .collect(Collectors.groupingBy(OwlSubClassOf::getValue))
            .values()
            .forEach(this::processGroupOfSubClassOfs);
  }

  private void processGroupOfSubClassOfs(List<OwlSubClassOf> subClassOfs) {
    // flatten superclasses into one list
    List<OwlClassReference> superClasses = subClassOfs.stream()
                                                      .map(OwlSubClassOf::getSuperClass)
                                                      .collect(Collectors.toList());

    // add missing classes
    superClasses.forEach(this::ensureTypeExists);

    List<String> superClassIds = superClasses.stream()
                                             .map(SingleValueRule::getValue)
                                             .distinct()
                                             .collect(Collectors.toList());

    OwlClassReference subClassReference = subClassOfs.get(0).getValue();
    applicationProfile.getTypeDef(subClassReference.getValue())
                      .addRule(new SubClassOf(superClassIds));
    owlRules.remove("New subClassOf", subClassOfs);
  }

  private void processProperties() {
    owlRules.getAssignableRules(PropertyReference.class)
            .stream()
            .filter(propertyReference -> !(propertyReference instanceof PropertyReferenceCandidate))
            .filter(propertyReference -> !intermediateAttributeMap.containsKey(propertyReference.getValue()))
            .forEach(propertyReference -> {
              String propertyUri = propertyReference.getValue();

              AttributeDef attribute = AttributeDef.newInstance();
              attribute.setUri(propertyUri);
              attribute.setAttributeId(propertyUri);

              IntermediateAttribute.Type attributeType = getAttributeType(propertyReference.getClass());
              IntermediateAttribute intermediateAttribute = new IntermediateAttribute(attribute, attributeType);
              intermediateAttributeMap.put(propertyUri, intermediateAttribute);
              owlRules.remove("New property", propertyReference);
            });
  }

  private IntermediateAttribute.Type getAttributeType(Class<?> attributeType) {
    if (attributeType.equals(DatatypePropertyReference.class)) return IntermediateAttribute.Type.datatype;
    if (attributeType.equals(ObjectPropertyReference.class)) return IntermediateAttribute.Type.object;
    return IntermediateAttribute.Type.unknown;
  }

  private void processRange() {
    owlRules.getExactRules(PropertyRange.class)
            .stream()
            // group by property
            .collect(Collectors.groupingBy(PropertyRange::getValue))
            .forEach(this::processGroupOfPropertyRanges);
  }

  private void processGroupOfPropertyRanges(PropertyReference propertyReference,
                                            List<PropertyRange> propertyRanges) {
    Rule rangeRule = toApplicationProfileRange(propertyRanges, PropertyRange::getRange);
    if (rangeRule == null) return;

    IntermediateAttribute attribute = intermediateAttributeMap.get(propertyReference.getValue());
    attribute.getAttributeDef().addRule(new Range(rangeRule));
    owlRules.remove("Property ranges", propertyRanges);
  }

  private void processDomain() {
    owlRules.getExactRules(PropertyDomain.class)
            .stream()
            // group by property
            .collect(Collectors.groupingBy(PropertyDomain::getValue))
            .forEach(this::processGroupOfPropertyDomains);
  }

  private void processGroupOfPropertyDomains(PropertyReference propertyReference,
                                             List<PropertyDomain> propertyDomains) {
    Rule domainRule = toApplicationProfileRange(propertyDomains, PropertyDomain::getDomain);
    if (domainRule == null) return;

    if (domainRule instanceof ClassId) {
      IntermediateAttribute intermediateAttribute = intermediateAttributeMap.get(propertyReference.getValue());
      applicationProfile.getTypeDef(((ClassId) domainRule).getValue())
                        .addAttributeDef(intermediateAttribute.getAttributeDef());

      owlRules.remove("Domain simple", propertyDomains);
    }
    else if (domainRule instanceof And) {
      And and = (And) domainRule;
      String summary = and.getValue().stream()
                          .map(rule -> {
                            if (rule instanceof ClassId) {
                              ClassId classId = (ClassId) rule;
                              return "class " + classId.getValue()
                                     + " with superclasses "
                                     + getSuperClasses(applicationProfile.getTypeDef(classId.getValue()));
                            }
                            else {
                              return rule.toString();
                            }
                          })
                          .collect(Collectors.joining("\n\t"));

      log.warn("Problem with And rule for property {} \n\t{}", propertyReference.getValue(), summary);
    }
    else {
      // TODO
      log.info("TODO: process => " + domainRule);
    }
  }

  private <T extends Rule> Rule toApplicationProfileRange(List<T> rangeRules,
                                                          Function<T, Rule> convertToApplicationProfile) {
    List<Rule> rule = rangeRules.stream()
                                .map(convertToApplicationProfile)
                                .collect(Collectors.toList());

    Rule rangeRule = rule.size() == 1 ? rule.get(0)
                                      : new And(rule);

    rangeRule = convertRangeRule(rangeRule);
    return rangeRule;
  }

  private Rule convertRangeRule(Rule rangeRule) {
    if (rangeRule instanceof OwlClassReferenceCandidate) {
      String datatypeOrClass = ((OwlClassReferenceCandidate) rangeRule).getValue();

      if (Constants.datatypes.contains(datatypeOrClass)) {
        return Datatype.datatype(datatypeOrClass);
      }

      if (!applicationProfile.hasTypeDef(datatypeOrClass)) {
        // try if there are candidates if missing
        boolean hasCandidate = owlRules.getExactRules(OwlClassReferenceCandidate.class)
                                       .stream()
                                       .anyMatch(r -> r.getValue().equals(datatypeOrClass));
        if (hasCandidate) {
          ensureTypeExists((Reference) rangeRule);
        }
      }

      if (!applicationProfile.hasTypeDef(datatypeOrClass)) {
        log.warn("Cannot find type {} in application profile.", datatypeOrClass);
      }

      return new ClassId(datatypeOrClass);
    }
    else if (rangeRule instanceof And) {
      List<Rule> conversion = ((And) rangeRule).getValue()
                                               .stream()
                                               .map(this::convertRangeRule)
                                               .collect(Collectors.toList());


      Set<Rule> redundant = conversion.stream()
                                      .map(r1 -> conversion.stream().map(r2 -> new Tuple2(r1, r2)))
                                      .flatMap(it -> it)
                                      .filter(tuple2 -> tuple2._1 == tuple2._2)
                                      .filter(tuple2 -> tuple2._1 instanceof ClassId)
                                      .filter(tuple2 -> tuple2._2 instanceof ClassId)
                                      .filter(tuple2 -> {
                                        boolean isSuperClass = false;

                                        TypeDef typeDef = applicationProfile.getTypeDef(((ClassId) tuple2._1).getValue());
                                        isSuperClass = getSuperClasses(typeDef).contains(((ClassId) tuple2._2).getValue());


                                        return isSuperClass;
                                      })
                                      .map(tuple2 -> ((ClassId) tuple2._2))
                                      .collect(Collectors.toSet());

      if (redundant.size() > 0) {
        log.warn("Removing redundant classes from And: {}", redundant);
        conversion.removeAll(redundant);
      }

      return conversion.size() == 1 ? conversion.get(0)
                                    : new And(conversion);
    }

    String ruleType = rangeRule.getClass().getSimpleName();
    log.warn("TODO: process range rule: {}", ruleType);
    throw new UnsupportedOperationException("Cannot convert rule of type: " + ruleType);
//    return null;
  }

  private void processCardinality() {
    owlRules.getExactRules(OwlExactCardinality.class)
            .forEach(this::processExactCardinality);

    owlRules.getExactRules(OwlMaxCardinality.class)
            .forEach(this::processMaxCardinality);

    owlRules.getExactRules(OwlMinCardinality.class)
            .forEach(this::processMinCardinality);
  }

  private void processExactCardinality(OwlCardinality cardinality) {
    String cardinalityTypeName = cardinality.getValue().getValue();
    TypeDef cardinalityType = applicationProfile.getTypeDef(cardinalityTypeName);

    String attributeId = cardinality.getProperty().getValue();
    if (cardinalityType.hasAttributeDef(attributeId)) {
      AttributeDef attributeDef = cardinalityType.getAttributeDef(attributeId);
      attributeDef.addRule(new MaxCardinality(cardinality.getCardinality()));
      attributeDef.addRule(new MinCardinality(cardinality.getCardinality()));

      owlRules.remove("Exact cardinality simple", cardinality);
    }
    else {
      Set<String> attributeClasses = getAttributeClasses(attributeId);
      Set<String> cardinalitySuperClasses = getSuperClasses(cardinalityType);

      Sets.SetView<String> intersection = Sets.intersection(attributeClasses, cardinalitySuperClasses);
      boolean cardinalityDomainMatches = intersection.size() > 0;
      if (cardinalityDomainMatches) {
        String typeToCopyFrom = intersection.iterator().next();
        BasicAttributeDef basicAttributeDef = getAttributeCopy(typeToCopyFrom, cardinalityType, attributeId);

        basicAttributeDef.addRule(new MinCardinality(cardinality.getCardinality()));
        basicAttributeDef.addRule(new MaxCardinality(cardinality.getCardinality()));
        owlRules.remove("Exact cardinality inheritance", cardinality);
      }
      else {
        logCardinalityWarning("exact", cardinalityTypeName, attributeId, attributeClasses, cardinalitySuperClasses);
      }
    }
  }

  private void processMaxCardinality(OwlCardinality cardinality) {
    String cardinalityTypeName = cardinality.getValue().getValue();
    TypeDef cardinalityType = applicationProfile.getTypeDef(cardinalityTypeName);

    String attributeId = cardinality.getProperty().getValue();
    if (cardinalityType.hasAttributeDef(attributeId)) {
      AttributeDef attributeDef = cardinalityType.getAttributeDef(attributeId);
      attributeDef.addRule(new MaxCardinality(cardinality.getCardinality()));

      owlRules.remove("Max cardinality simple", cardinality);
    }
    else {
      Set<String> attributeClasses = getAttributeClasses(attributeId);
      Set<String> cardinalitySuperClasses = getSuperClasses(cardinalityType);

      Sets.SetView<String> intersection = Sets.intersection(attributeClasses, cardinalitySuperClasses);
      boolean cardinalityDomainMatches = intersection.size() > 0;
      if (cardinalityDomainMatches) {
        String typeToCopyFrom = intersection.iterator().next();
        BasicAttributeDef basicAttributeDef = getAttributeCopy(typeToCopyFrom, cardinalityType, attributeId);

        basicAttributeDef.addRule(new MaxCardinality(cardinality.getCardinality()));
        owlRules.remove("Max cardinality inheritance", cardinality);
      }
      else {
        logCardinalityWarning("max", cardinalityTypeName, attributeId, attributeClasses, cardinalitySuperClasses);
      }
    }
  }

  private void processMinCardinality(OwlCardinality cardinality) {
    String cardinalityTypeName = cardinality.getValue().getValue();
    TypeDef cardinalityType = applicationProfile.getTypeDef(cardinalityTypeName);

    String attributeId = cardinality.getProperty().getValue();
    if (cardinalityType.hasAttributeDef(attributeId)) {
      AttributeDef cardinalityAttribute = cardinalityType.getAttributeDef(attributeId);
      cardinalityAttribute.addRule(new MinCardinality(cardinality.getCardinality()));

      owlRules.remove("Min cardinality simple", cardinality);
    }
    else {
      Set<String> attributeClasses = getAttributeClasses(attributeId);
      Set<String> cardinalitySuperClasses = getSuperClasses(cardinalityType);

      Sets.SetView<String> intersection = Sets.intersection(attributeClasses, cardinalitySuperClasses);
      boolean cardinalityDomainMatches = intersection.size() > 0;
      if (cardinalityDomainMatches) {
        String typeToCopyFrom = intersection.iterator().next();
        BasicAttributeDef basicAttributeDef = getAttributeCopy(typeToCopyFrom, cardinalityType, attributeId);

        basicAttributeDef.addRule(new MinCardinality(cardinality.getCardinality()));
        owlRules.remove("Min cardinality inheritance", cardinality);
      }
      else {
        logCardinalityWarning("min", cardinalityTypeName, attributeId, attributeClasses, cardinalitySuperClasses);
      }
    }
  }

  private void logCardinalityWarning(String cardinalityKind,
                                     String cardinalityTypeName,
                                     String attributeId,
                                     Set<String> attributeClasses,
                                     Set<String> cardinalitySuperClasses) {
    log.warn("problem for {} cardinality: \n" +
             "  type:      {}\n" +
             "  attribute: {}\n" +
             "  attribute domain : {}\n" +
             "  type superclasses: {}\n",
             cardinalityKind,
             cardinalityTypeName,
             attributeId,
             attributeClasses,
             cardinalitySuperClasses
            );
  }

  private BasicAttributeDef getAttributeCopy(String typeToCopyFrom, TypeDef typeToCopyTo, String attributeId) {
    TypeDef typeDef = applicationProfile.getTypeDef(typeToCopyFrom);
    AttributeDef attributeDef = typeDef.getAttributeDef(attributeId);

    BasicAttributeDef basicAttributeDef = new BasicAttributeDef();
    basicAttributeDef.setTypeDef(typeToCopyTo);
    basicAttributeDef.setAttributeId(attributeDef.getAttributeId());
    basicAttributeDef.setUri(attributeDef.getUri());
    basicAttributeDef.setRules(attributeDef.getRules().stream().map(r -> r.copy()).collect(Collectors.toList()));
    return basicAttributeDef;
  }

  private Set<String> getSuperClasses(TypeDef typeDef) {
    return typeDef.getRules(SubClassOf.class).stream()
                  .flatMap(subClassOf -> subClassOf.getValue().stream())
                  .collect(Collectors.toSet());
  }

  private Set<String> getAttributeClasses(String attributeId) {
    return getAttributes(attributeId).stream()
                                     .map(AttributeDef::getTypeDef)
                                     .map(TypeDef::getClassId)
                                     .collect(Collectors.toSet());
  }

  private Predicate<AttributeDef> isAttributeTypeSuperclassOf(String type) {
    return attributeDef -> {
      return attributeDef.getTypeDef()
                         .getRules(SubClassOf.class)
                         .stream()
                         .anyMatch(subClassOf -> subClassOf.getValue().contains(type));
    };
  }

  private void logAttributes(String attributeId) {
    List<AttributeDef> attributes = getAttributes(attributeId);
    log.info("attributes found: {}", attributes.size());
    attributes.forEach(attributeDef -> {
      log.info("\t\t{} {}", attributeDef.getTypeDef().getClassId(), attributeDef.getAttributeId());
    });
  }

  private List<AttributeDef> getAttributes(String attributeId) {
    return applicationProfile.getTypeDefs().values()
                             .stream()
                             .flatMap(typeDef -> typeDef.getAttributeDefs().values().stream())
                             .filter(attributeDef -> attributeDef.getAttributeId().equals(attributeId))
                             .collect(Collectors.toList());
  }

  private List<String> getPropertiesOfExtraRules(List<OwlExtra> extraRules) {
    return extraRules.stream()
                     .map(extra -> extra.getProperty().getValue())
                     .distinct()
                     .collect(Collectors.toList());
  }

  private List<OwlExtra> getExtraRules(boolean type) {
    return owlRules.getExactRules(OwlExtra.class)
                   .stream()
                   .filter(extra -> type == applicationProfile.hasTypeDef(extra.getValue().getValue()))
                   .collect(Collectors.toList());
  }


}
