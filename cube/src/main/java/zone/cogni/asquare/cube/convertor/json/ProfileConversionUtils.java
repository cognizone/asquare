package zone.cogni.asquare.cube.convertor.json;

import org.apache.commons.collections4.CollectionUtils;
import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.And;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.MaxCardinality;
import zone.cogni.asquare.applicationprofile.rules.MaxLangCardinality;
import zone.cogni.asquare.applicationprofile.rules.Not;
import zone.cogni.asquare.applicationprofile.rules.Or;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.asquare.applicationprofile.rules.RdfTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Internal helper class because a lot of the conversion need the same logic.
 */
@SuppressWarnings({"RedundantIfStatement", "Convert2MethodRef"})
class ProfileConversionUtils {

  /**
   * @return a default set of prefixes. here because ApplicationProfile does not have prefixes.
   */
  public static Map<String, String> getPrefixes() {
    Map<String, String> result = new HashMap<>();
    result.put("dct", "http://purl.org/dc/terms/");
    result.put("jolux", "http://data.legilux.public.lu/resource/ontology/jolux#");
    result.put("owl", "http://www.w3.org/2002/07/owl#");
    result.put("prov", "http://www.w3.org/ns/prov#");
    result.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
    result.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
    result.put("schema", "http://schema.org/");
    result.put("skos", "http://www.w3.org/2004/02/skos/core#");
    result.put("xsd", "http://www.w3.org/2001/XMLSchema#");
    return result;
  }

  public static String getRootRdfType(ApplicationProfile.Type type) {
    Collection<String> rootRdfTypes = calculateRootRdfTypes(type);

    if (rootRdfTypes.isEmpty())
      throw new RuntimeException("No root rdf class found for type " + type.getClassId());

    if (rootRdfTypes.size() > 1) {
      String message = "Expecting a simplified model with exactly 1 rdf class for type " + type.getClassId();
      throw new RuntimeException(message);
    }

    return rootRdfTypes.stream().findFirst().get();
  }

  private static Collection<String> calculateRootRdfTypes(ApplicationProfile.Type type) {
    Collection<String> allTypes = getRdfTypes(type);
    Collection<String> superTypes = getRdfTypeStrings(type.getApplicationProfile(), getRealSuperClassIds(type));
    return CollectionUtils.removeAll(allTypes, superTypes);
  }

  public static Collection<String> getRdfTypes(ApplicationProfile.Type type) {
    ApplicationProfile applicationProfile = type.getApplicationProfile();
    Set<String> superClassIds = type.getSuperClassIds();
    return getRdfTypeStrings(applicationProfile, superClassIds);
  }

  private static Set<String> getRealSuperClassIds(ApplicationProfile.Type apType) {
    Set<String> superClassIds = new HashSet<>(apType.getSuperClassIds());
    superClassIds.remove(apType.getClassId());
    return superClassIds;
  }

  private static Collection<String> getRdfTypeStrings(ApplicationProfile applicationProfile,
                                                      Set<String> superClassIds) {
    return superClassIds.stream()
                        .map(applicationProfile::getType)
                        .flatMap(ProfileConversionUtils::getRdfTypeStrings)
                        .collect(Collectors.toSet());
  }

  private static Stream<String> getRdfTypeStrings(ApplicationProfile.Type type) {
    Collection<String> result = new HashSet<>();
    type.getRules(RdfType.class)
        .forEach(r -> result.add(r.getValue()));
    type.getRules(RdfTypes.class)
        .forEach(r -> result.addAll(r.getValue()));
    return result.stream();
  }

  /**
   * @return type of attribute for JSON HAL based on Range rules in application profile
   */
  public static ConversionProfile.Attribute.Type getAttributeType(ApplicationProfileDef.AttributeDef attributeDef) {
    Set<Class<?>> classes = attributeDef.getRules(Range.class)
                                        .stream()
                                        .flatMap(range -> getLeafRules(range).stream())
                                        .map(rule -> rule.getClass())
                                        .collect(Collectors.toSet());

    if (classes.size() == 1 && classes.contains(ClassId.class))
      return ConversionProfile.Attribute.Type.reference;
    if (classes.size() == 1 && classes.contains(Datatype.class))
      return ConversionProfile.Attribute.Type.attribute;
    return ConversionProfile.Attribute.Type.mix;
  }


  /**
   * @return type of attribute for JSON HAL based on Range rules in application profile
   */
  public static ConversionProfile.Attribute.Type getAttributeType(ApplicationProfile.Attribute attribute) {
    Set<Class<?>> classes = attribute.getAttributeDef()
                                     .stream()
                                     .flatMap(attributeDef -> attributeDef.getRules(Range.class).stream())
                                     .flatMap(range -> getLeafRules(range).stream())
                                     .map(rule -> rule.getClass())
                                     .collect(Collectors.toSet());

    if (classes.size() == 1 && classes.contains(ClassId.class))
      return ConversionProfile.Attribute.Type.reference;
    if (classes.size() == 1 && classes.contains(Datatype.class))
      return ConversionProfile.Attribute.Type.attribute;
    return ConversionProfile.Attribute.Type.mix;
  }

  private static List<Rule<?>> getLeafRules(Rule<?> rule) {
    List<Rule<?>> all = new ArrayList<>();
    getLeafRules(rule, all);

    return all;
  }

  private static void getLeafRules(Rule<?> rule, List<Rule<?>> all) {
    if (rule instanceof ClassId) {
      all.add(rule);
      return;
    }
    if (rule instanceof Datatype) {
      all.add(rule);
      return;
    }
    if (rule instanceof Range) {
      getLeafRules(((Range) rule).getValue(), all);
      return;
    }
    if (rule instanceof Not) {
      getLeafRules(((Not) rule).getValue(), all);
      return;
    }
    if (rule instanceof Or) {
      ((Or) rule).getValue()
                 .forEach(r -> getLeafRules(r, all));
      return;
    }
    if (rule instanceof And) {
      ((And) rule).getValue()
                  .forEach(r -> getLeafRules(r, all));
      return;
    }

    throw new RuntimeException("Not here???");
  }

  /**
   * @return true if attribute has max cardinality 1
   */
  public static boolean isSingle(ApplicationProfileDef.AttributeDef attribute) {
    Class<MaxCardinality> type = MaxCardinality.class;

    Integer maxCardinality = attribute.getRule(MaxCardinality.class).map(r -> r.getValue()).getOrElse((Integer) null);
    Integer maxLangCardinality = attribute.getRule(MaxLangCardinality.class).map(r -> r.getValue()).getOrElse((Integer) null);

    if (maxCardinality == null && maxLangCardinality == null) return false;
    if (maxCardinality != null && maxCardinality <= 1) return true;
    if (maxLangCardinality != null && maxLangCardinality <= 1) return true;

    return false;
  }


  /**
   * @return true if attribute has max cardinality 1
   */
  public static boolean isSingle(ApplicationProfile.Attribute attribute) {
    Integer maxCardinality = getMinOfMaxCardinality(attribute, MaxCardinality.class);
    Integer maxLangCardinality = getMinOfMaxCardinality(attribute, MaxLangCardinality.class);

    if (maxCardinality == null && maxLangCardinality == null) return false;
    if (maxCardinality != null && maxCardinality <= 1) return true;
    if (maxLangCardinality != null && maxLangCardinality <= 1) return true;

    return false;
  }

  private static Integer getMinOfMaxCardinality(ApplicationProfile.Attribute attribute, Class<? extends SingleValueRule<Integer>> type) {
    return attribute.getAttributeDef()
                    .stream()
                    .flatMap(attributeDef -> attributeDef.getRules(type).stream())
                    .map(SingleValueRule::getValue)
                    .min(Comparator.comparingInt(Integer::intValue))
                    .orElse(null);
  }

}
