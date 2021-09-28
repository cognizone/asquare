package zone.cogni.asquare.access;


import com.google.common.base.Preconditions;
import org.springframework.core.io.Resource;

import java.util.function.Supplier;

public class ResourceSupplier implements Supplier<Resource> {

  private Resource resource;

  public Resource getResource() {
    return resource;
  }

  public void setResource(Resource resource) {
    this.resource = resource;
  }

  @Override
  public Resource get() {
    Preconditions.checkState(resource != null, "Resource must be set.");
    return resource;
  }

}
