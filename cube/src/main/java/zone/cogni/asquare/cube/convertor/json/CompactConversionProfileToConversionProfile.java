package zone.cogni.asquare.cube.convertor.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class CompactConversionProfileToConversionProfile
        implements Function<CompactConversionProfile, ConversionProfile> {

  private static final Logger log = LoggerFactory.getLogger(CompactConversionProfileToConversionProfile.class);

  @Override
  public ConversionProfile apply(CompactConversionProfile compactConversionProfile) {
    CompactConversionProfile collapsedConversionProfile = getCollapsedImportsConversionProfile(compactConversionProfile);
    ConversionProfile result = new ConversionProfile();

    if (collapsedConversionProfile.getPrefixes() != null)
      result.setPrefixes(collapsedConversionProfile.getPrefixes());

    collapsedConversionProfile.getTypes()
                              .forEach(type -> result.add(convertType(type)));

    result.done();
    return result;
  }

  private CompactConversionProfile getCollapsedImportsConversionProfile(CompactConversionProfile compactConversionProfile) {
    return new CollapsedImportsCompactConversionProfile().apply(compactConversionProfile);
  }

  private ConversionProfile.Type convertType(CompactConversionProfile.Type type) {
    ConversionProfile.Type expandedType = new ConversionProfile.Type();
    expandedType.setRootClassId(type.getId());
    expandedType.setRootRdfType(type.getType());
    expandedType.setClassIds(calculateClassIds(type));
    expandedType.setRdfTypes(calculateRdfTypes(type));

    getAllAttributes(type).forEach(expandedType::add);

    return expandedType;
  }

  private Collection<ConversionProfile.Attribute> getAllAttributes(CompactConversionProfile.Type type) {
    Map<String, ConversionProfile.Attribute> attributeMap = new HashMap<>();

    getAllAttributes(type, attributeMap);
    return attributeMap.values();
  }

  private void getAllAttributes(CompactConversionProfile.Type type,
                                Map<String, ConversionProfile.Attribute> attributeMap) {
    List<CompactConversionProfile.Attribute> attributes = type.getAttributes();
    attributes
            .forEach(attribute -> addAttribute(attributeMap, attribute));

    type.getRealSuperClasses()
        .forEach(superId -> getAllAttributes(type.getConversionProfile().getById(superId), attributeMap));
  }

  private void addAttribute(Map<String, ConversionProfile.Attribute> attributeMap,
                            CompactConversionProfile.Attribute attribute) {
    if (!attributeMap.containsKey(attribute.getId())) {
      attributeMap.put(attribute.getId(), convertAttribute(attribute));
      return;
    }

    ConversionProfile.Attribute currentAttribute = attributeMap.get(attribute.getId());
    merge(currentAttribute, attribute);
  }

  private Collection<String> calculateClassIds(CompactConversionProfile.Type type) {
    HashSet<String> result = new HashSet<>();

    calculateClassIds(type, result);
    return result;
  }

  private void calculateClassIds(CompactConversionProfile.Type type,
                                 Set<String> classIds) {
    classIds.add(type.getId());

    type.getRealSuperClasses()
        .forEach(superId -> calculateClassIds(type.getConversionProfile().getById(superId), classIds));
  }

  private Collection<String> calculateRdfTypes(CompactConversionProfile.Type type) {
    HashSet<String> result = new HashSet<>();

    calculateRdfTypes(type, result);
    return result;
  }

  private void calculateRdfTypes(CompactConversionProfile.Type type,
                                 Set<String> rdfTypes) {
    rdfTypes.add(type.getType());

    type.getRealSuperClasses()
        .forEach(superId -> calculateRdfTypes(type.getConversionProfile().getById(superId), rdfTypes));
  }

  private ConversionProfile.Attribute convertAttribute(CompactConversionProfile.Attribute attribute) {
    ConversionProfile.Attribute convertedAttribute = new ConversionProfile.Attribute();

    convertedAttribute.setAttributeId(attribute.getId());
    convertedAttribute.setUri(attribute.getProperty());
    convertedAttribute.setSingle(attribute.isSingle());
    convertedAttribute.setInverse(attribute.isInverse());
    convertedAttribute.setType(attribute.getType().toExpandedAttributeType());

    return convertedAttribute;
  }

  private void merge(ConversionProfile.Attribute currentAttribute, CompactConversionProfile.Attribute newAttribute) {
    // id
    if (!Objects.equals(currentAttribute.getAttributeId(), newAttribute.getId()))
      throw new RuntimeException("Attribute ids do not match: "
                                 + currentAttribute.getAttributeId() + " and " + newAttribute.getId());

    // uri
    if (!Objects.equals(currentAttribute.getUri(), newAttribute.getProperty()))
      throw new RuntimeException("Attributes uris do not match: "
                                 + currentAttribute.getUri() + " and " + newAttribute.getProperty());

    // cardinality
    if (currentAttribute.isSingle() != newAttribute.isSingle()) {
      currentAttribute.setSingle(true);
    }

    // inverse
    if (currentAttribute.isInverse() != newAttribute.isInverse()) {
      currentAttribute.setInverse(newAttribute.isInverse());
    }

    // type (object or datatype)
    if (!Objects.equals(currentAttribute.getType(), newAttribute.getType().toExpandedAttributeType()))
      throw new RuntimeException("Attributes types do not match: "
                                 + currentAttribute.getType() + " and " + newAttribute.getType()
                                                                                      .toExpandedAttributeType());

  }

}
