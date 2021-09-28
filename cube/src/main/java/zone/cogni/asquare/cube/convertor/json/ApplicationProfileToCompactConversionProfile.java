package zone.cogni.asquare.cube.convertor.json;

import com.google.common.base.Preconditions;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ApplicationProfileToCompactConversionProfile
  implements Function<ApplicationProfile, CompactConversionProfile> {

  @Override
  public CompactConversionProfile apply(ApplicationProfile applicationProfile) {
    Preconditions.checkNotNull(applicationProfile);

    CompactConversionProfile result = new CompactConversionProfile();
    result.setPrefixes(ProfileConversionUtils.getPrefixes());
    result.setTypes(getTypes(applicationProfile));
    return result;
  }

  @Nonnull
  private List<CompactConversionProfile.Type> getTypes(ApplicationProfile applicationProfile) {
    return applicationProfile.getTypes()
                             .values()
                             .stream()
                             .map(this::convertType)
                             .collect(Collectors.toList());
  }

  private CompactConversionProfile.Type convertType(ApplicationProfile.Type apType) {
    CompactConversionProfile.Type type = new CompactConversionProfile.Type();
    type.setId(apType.getClassId());
    type.setType(ProfileConversionUtils.getRootRdfType(apType));
    // TODO probably only partially correct... also takes super-superclasses
    type.setSuperClasses(apType.getSuperClassIds());
    type.setAttributes(getAttributes(apType));
    return type;
  }

  @Nonnull
  private List<CompactConversionProfile.Attribute> getAttributes(ApplicationProfile.Type apType) {
    return getTypeAttributeIds(apType)
      .stream()
      .map(this::convertAttribute)
      .collect(Collectors.toList());
  }

  private List<ApplicationProfileDef.AttributeDef> getTypeAttributeIds(ApplicationProfile.Type apType) {
    return apType.getTypeDef()
                 .stream()
                 .map(typeDef -> typeDef.getAttributeDefs().values())
                 .flatMap(Collection::stream)
                 .collect(Collectors.toList());
  }

  private CompactConversionProfile.Attribute convertAttribute(ApplicationProfileDef.AttributeDef apAttribute) {
    CompactConversionProfile.Attribute attribute = new CompactConversionProfile.Attribute();

    attribute.setId(apAttribute.getAttributeId());
    attribute.setProperty(apAttribute.getUri());
    attribute.setSingle(ProfileConversionUtils.isSingle(apAttribute));
    attribute.setType(ProfileConversionUtils.getAttributeType(apAttribute).toCompactAttributeType());

    return attribute;
  }

}
