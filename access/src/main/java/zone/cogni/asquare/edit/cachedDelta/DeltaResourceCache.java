package zone.cogni.asquare.edit.cachedDelta;

import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.DeltaResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeltaResourceCache {
  // todo : Key = uri + type
  private final Map<String, DeltaResource> cache;

  public DeltaResourceCache() {
    cache = new HashMap<>();
  }

  public List<DeltaResource> getAllResources() {
    return new ArrayList<>(cache.values());
  }

  public void cacheResource(DeltaResource resource) {
    cache.put(resource.getResource().getURI(), resource);
  }

  public DeltaResource getResource(String resourceUri, ApplicationProfile.Type type) {
    DeltaResource deltaResource = cache.get(resourceUri);
    if (deltaResource == null) throw new RuntimeException(type.getClassId() + " '" + resourceUri + "' is not cached");
    return deltaResource;
  }

  public boolean hasResource(String resourceUri, ApplicationProfile.Type type) {
    return cache.containsKey(resourceUri);
  }
}
