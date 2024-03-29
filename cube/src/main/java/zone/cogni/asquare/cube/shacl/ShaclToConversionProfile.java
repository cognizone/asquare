package zone.cogni.asquare.cube.shacl;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfile;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ShaclToConversionProfile implements Function<Model, CompactConversionProfile> {

  @Override
  @Nonnull
  public CompactConversionProfile apply(@Nonnull Model shacl) {
    // TODO think a namespace normalizer?
    CompactConversionProfile profile = new CompactConversionProfile();
    profile.getContext().setPrefixes(shacl.getNsPrefixMap());
    profile.setTypes(calculateTypes(profile, shacl));
    return profile;
  }

  @Nonnull
  private List<CompactConversionProfile.Type> calculateTypes(@Nonnull CompactConversionProfile profile,
                                                             @Nonnull Model shacl) {
    // TODO add a check for ShapesGraph
    return shacl.listStatements(null, RDF.type, SHACLM.NodeShape)
                .toList()
                .stream()
                .map(Statement::getSubject)
                .map(nodeShape -> calculateType(profile, shacl, nodeShape))
                .collect(Collectors.toList());
  }

  @Nonnull
  private CompactConversionProfile.Type calculateType(@Nonnull CompactConversionProfile profile,
                                                      @Nonnull Model shacl,
                                                      @Nonnull Resource nodeShape) {
    CompactConversionProfile.Type type = new CompactConversionProfile.Type();
    type.setSuperClasses(calculateSuperClasses(profile, shacl, nodeShape));
    type.setId(calculateTargetClassId(profile, nodeShape));
    type.setType(calculateRdfType(nodeShape));
    type.setAttributes(calculateAttributes(profile, shacl, nodeShape));
    return type;
  }

  @Nonnull
  private String calculateTargetClassId(@Nonnull CompactConversionProfile profile,
                                        @Nonnull Resource shape) {
    Resource resource = shape.getPropertyResourceValue(SHACLM.targetClass);
    return convertResourceToId(profile, resource);
  }

  @Nonnull
  private String convertResourceToId(@Nonnull CompactConversionProfile profile,
                                     @Nonnull Resource resource) {
    String uri = resource.getURI();
    Optional<String> optionalId = profile.getContext()
                                         .getPrefixes()
                                         .entrySet()
                                         .stream()
                                         .filter(entry -> uri.startsWith(entry.getValue()))
                                         .map(entry -> shortenUri(uri, entry))
                                         .findFirst();

    return optionalId.orElse(uri);
  }

  private String shortenUri(String uri, Map.Entry<String, String> prefix) {
    String localPart = StringUtils.substringAfter(uri, prefix.getValue());
    return prefix.getKey() + ":" + localPart;
  }

  @Nonnull
  private String calculateRdfType(@Nonnull Resource nodeShape) {
    return nodeShape.getPropertyResourceValue(SHACLM.targetClass).getURI();
  }

  @Nonnull
  private Set<String> calculateSuperClasses(@Nonnull CompactConversionProfile profile,
                                            @Nonnull Model shacl,
                                            @Nonnull Resource nodeShape) {
    return getSubclasses(shacl, nodeShape)
            .stream()
            .map(superClass -> calculateTargetClassId(profile, superClass))
            .collect(Collectors.toSet());
  }

  @Nonnull
  private List<Resource> getSubclasses(@Nonnull Model shacl,
                                       @Nonnull Resource nodeShape) {
    return shacl.listStatements(nodeShape, RDFS.subClassOf, (RDFNode) null)
                .toList()
                .stream()
                .map(Statement::getObject)
                .map(RDFNode::asResource)
                .collect(Collectors.toList());
  }

  @Nonnull
  private List<CompactConversionProfile.Attribute> calculateAttributes(@Nonnull CompactConversionProfile profile,
                                                                       @Nonnull Model shacl,
                                                                       @Nonnull Resource nodeShape) {
    return nodeShape.listProperties(SHACLM.property)
                    .toList()
                    .stream()
                    .map(statement -> statement.getObject().asResource())
                    .map(propertyShape -> calculateAttribute(profile, shacl, nodeShape, propertyShape))
                    .collect(Collectors.toList());
  }

  @Nonnull
  private CompactConversionProfile.Attribute calculateAttribute(@Nonnull CompactConversionProfile profile,
                                                                @Nonnull Model shacl,
                                                                @Nonnull Resource nodeShape,
                                                                @Nonnull Resource propertyShape) {

    CompactConversionProfile.Attribute attribute = new CompactConversionProfile.Attribute();
    attribute.setId(calculatePathId(profile, propertyShape));
    attribute.setProperty(calculateProperty(propertyShape));
    attribute.setSingle(calculateSingle(propertyShape));
    attribute.setType(calculateAttributeType(propertyShape));
    attribute.setInverse(isInversePropertyShape(propertyShape));
    return attribute;
  }

  private boolean isInversePropertyShape(Resource propertyShape) {
    boolean hasPath = propertyShape.hasProperty(SHACLM.path);
    boolean hasInversePath = propertyShape.hasProperty(SHACLM.inversePath);

    if (hasPath && hasInversePath)
      throw new RuntimeException("both 'path' or 'inversePath' on property '" + propertyShape.getURI() + "'");

    if (hasPath) return false;
    if (hasInversePath) return true;

    throw new RuntimeException("missing 'path' or 'inversePath' on property '" + propertyShape.getURI() + "'");
  }

  @Nonnull
  private String calculatePathId(@Nonnull CompactConversionProfile profile,
                                 @Nonnull Resource propertyShape) {
    Resource resource = getPathOrInversePathResource(propertyShape);
    return convertResourceToId(profile, resource);
  }

  @Nonnull
  private String calculateProperty(@Nonnull Resource propertyShape) {
    return getPathOrInversePathResource(propertyShape).getURI();
  }

  private Resource getPathOrInversePathResource(Resource propertyShape) {
    Property pathProperty = isInversePropertyShape(propertyShape) ? SHACLM.inversePath : SHACLM.path;
    return propertyShape.getPropertyResourceValue(pathProperty);
  }

  @SuppressWarnings("UnnecessaryLocalVariable")
  private boolean calculateSingle(Resource propertyShape) {
    if (!propertyShape.hasProperty(SHACLM.maxCount))
      return false;

    List<Long> maxCounts = propertyShape.listProperties(SHACLM.maxCount)
                                        .toList()
                                        .stream()
                                        .map(Statement::getObject)
                                        .map(RDFNode::asLiteral)
                                        .map(Literal::getLong)
                                        .collect(Collectors.toList());

    if (maxCounts.size() != 1)
      throw new RuntimeException("expected exactly one maxCount: " + maxCounts);

    boolean isSingle = maxCounts.get(0) == 1;
    return isSingle;
  }

  private CompactConversionProfile.Attribute.Type calculateAttributeType(Resource propertyShape) {
    if (propertyShape.hasProperty(SHACLM.nodeKind)) {
      Resource nodeKind = propertyShape.getPropertyResourceValue(SHACLM.nodeKind);

      if (nodeKind.equals(SHACLM.Literal))
        return CompactConversionProfile.Attribute.Type.datatype;
      else if (nodeKind.equals(SHACLM.IRI) && !propertyShape.hasProperty(SHACLM.class_))
        return CompactConversionProfile.Attribute.Type.datatype;
      else if (nodeKind.equals(SHACLM.IRI))
        return CompactConversionProfile.Attribute.Type.object;
    }

    if (propertyShape.hasProperty(SHACLM.datatype)) {
      return CompactConversionProfile.Attribute.Type.datatype;
    }
    else if (propertyShape.hasProperty(SHACLM.class_)) {
      return CompactConversionProfile.Attribute.Type.object;
    }

    return CompactConversionProfile.Attribute.Type.mix;
  }

}
