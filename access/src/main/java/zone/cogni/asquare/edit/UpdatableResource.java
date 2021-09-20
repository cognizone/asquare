package zone.cogni.asquare.edit;

import org.apache.jena.rdf.model.Resource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import javax.annotation.Nonnull;
import java.util.List;

@Deprecated
public class UpdatableResource implements TypedResource {

  private final TypedResource newResource;
  private final TypedResource oldResource;

  public UpdatableResource(TypedResource newResource, TypedResource oldResource) {
    this.oldResource = oldResource;
    this.newResource = newResource;
  }

  @Nonnull
  @Override
  public Resource getResource() {
    return oldResource.getResource();
  }

  @Nonnull
  @Override
  public ApplicationProfile.Type getType() {
    return oldResource.getType();
  }

  @Nonnull
  @Override
  public <T extends RdfValue> List<T> getValues(@Nonnull ApplicationProfile.Attribute attribute) {
    //todo get old if new is not there
    return newResource.getValues(attribute);
  }

  public <T extends RdfValue> List<T> getNewValues(ApplicationProfile.Attribute attribute) {
    return newResource.getValues(attribute);
  }

  public <T extends RdfValue> List<T> getOldValues(ApplicationProfile.Attribute attribute) {
      return newResource.getValues(attribute);
  }

  // todo
  // get/calculate deleted attributes and/or attribute values?
  public void getDeleted() {
  }

  // todo
  // get/calculate new attributes and/or attribute values?
  public void getNew() {
  }


}
