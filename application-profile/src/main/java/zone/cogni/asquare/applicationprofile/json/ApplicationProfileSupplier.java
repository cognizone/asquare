package zone.cogni.asquare.applicationprofile.json;

import com.google.common.base.Preconditions;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

import java.util.function.Supplier;

public class ApplicationProfileSupplier implements Supplier<ApplicationProfile> {

  private final ApplicationProfileConfig applicationProfileConfig;

  private Supplier<Resource> resourceSupplier;

  public ApplicationProfileSupplier(ApplicationProfileConfig applicationProfileConfig) {
    this.applicationProfileConfig = applicationProfileConfig;
  }

  public Supplier<Resource> getResource() {
    return resourceSupplier;
  }

  public void setResource(Supplier<Resource> resourceSupplier) {
    this.resourceSupplier = resourceSupplier;
  }

  @SuppressWarnings({"unchecked", "CastToIncompatibleInterface"})
  @Override
  public ApplicationProfile get() {
    Preconditions.checkState(resourceSupplier != null, "Resource not set.");

    ApplicationProfileDeserializer deserializer = applicationProfileConfig.getDeserializer();
    return deserializer.apply(resourceSupplier.get());
  }
}
