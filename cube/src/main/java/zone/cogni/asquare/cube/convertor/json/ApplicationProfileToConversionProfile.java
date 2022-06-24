package zone.cogni.asquare.cube.convertor.json;

import com.google.common.base.Preconditions;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

import java.util.function.Function;

/**
 * Conversion class to go from ApplicationProfile to a "simple" class which can be used for
 * - conversions from RDF to JSON
 * - conversions from JSON to RDF
 */
public class ApplicationProfileToConversionProfile implements Function<ApplicationProfile, ConversionProfile> {
  @Override
  @Deprecated
  public ConversionProfile apply(ApplicationProfile applicationProfile) {
    Preconditions.checkNotNull(applicationProfile);

    ConversionProfile result = new ConversionProfile();
    result.getContext().setPrefixes(ProfileConversionUtils.getPrefixes());
    applicationProfile.getTypes()
                      .values()
                      .forEach(type -> result.add(convertType(type)));
    result.done();
    return result;
  }


  private ConversionProfile.Type convertType(ApplicationProfile.Type apType) {
    ConversionProfile.Type type = new ConversionProfile.Type();
    type.setRootClassId(apType.getClassId());
    type.setClassIds(apType.getSuperClassIds());
    type.setRdfTypes(ProfileConversionUtils.getRdfTypes(apType));
    type.setRootRdfType(ProfileConversionUtils.getRootRdfType(apType));

    apType.getAttributes()
          .values()
          .forEach(attribute -> type.add(convertAttribute(attribute)));

    return type;
  }


  private ConversionProfile.Attribute convertAttribute(ApplicationProfile.Attribute apAttribute) {
    ConversionProfile.Attribute attribute = new ConversionProfile.Attribute();

    attribute.setAttributeId(apAttribute.getAttributeId());
    attribute.setUri(apAttribute.getUri());
    attribute.setSingle(ProfileConversionUtils.isSingle(apAttribute));
    attribute.setType(ProfileConversionUtils.getAttributeType(apAttribute));

    return attribute;
  }

}
