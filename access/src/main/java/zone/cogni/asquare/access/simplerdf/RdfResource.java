package zone.cogni.asquare.access.simplerdf;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Resource;
import zone.cogni.asquare.access.AccessService;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class RdfResource implements TypedResource {

  protected final AccessService accessService;

  protected Resource resource;
  protected ApplicationProfile.Type type;

  public RdfResource(AccessService accessService) {
    this.accessService = accessService;
  }

  @Nonnull
  @Override
  public ApplicationProfile.Type getType() {
    return type;
  }

  public void setType(ApplicationProfile.Type type) {
    Objects.requireNonNull(type);
    this.type = type;
  }

  @Nonnull
  @Override
  public Resource getResource() {
    return resource;
  }

  public void setResource(Resource resource) {
    Objects.requireNonNull(resource);
    this.resource = resource;
  }

  @Nonnull
  @Override
  public <T extends RdfValue> List<T> getValues(@Nonnull ApplicationProfile.Attribute attribute) {
    // TODO this seems to be a weird fix (might be ignoring a more fundamental problem)
    Set<String> classIds = attribute.getType().getSuperClassIds();
    Preconditions.checkArgument(Objects.equals(type, attribute.getType())
                                || type.getSuperClassIds().stream().anyMatch(classIds::contains), "illegal attribute [ id '" + attribute.getAttributeId() + "' on type [" + attribute.getType().getClassIds() + "] retrieved from resource with type [" + type.getClassIds() + "]");

    Preconditions.checkArgument(type.hasAttribute(attribute.getAttributeId()),
            "Attribute not found.");

    return accessService.getValues(getApplicationProfile(), this, attribute);
  }

  @Override
  public String toString() {
    return "RdfResource {" +
           "type =[" + type.getDescription() + "]" +
           ", resource=" + resource +
           '}';
  }

}
