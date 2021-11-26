package zone.cogni.asquare.cube.index;

import org.springframework.core.io.Resource;

import java.util.List;
import java.util.stream.Collectors;

class IndexMetadata {

  private String name;
  private Resource settingsResource;
  private List<CollectionMetadata> collections;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Resource getSettingsResource() {
    return settingsResource;
  }

  public void setSettingsResource(Resource settingsResource) {
    this.settingsResource = settingsResource;
  }

  public boolean isValidSettingsResource() {
    return settingsResource != null && settingsResource.exists();
  }

  public List<CollectionMetadata> getCollections() {
    return collections;
  }

  public List<CollectionMetadata> getValidCollections() {
    return collections.stream()
                      .filter(CollectionMetadata::isValid)
                      .collect(Collectors.toList());
  }

  public CollectionMetadata getValidCollection(String name) {
    return collections.stream()
                      .filter(CollectionMetadata::isValid)
                      .filter(collection -> collection.getName().equals(name))
                      .findFirst()
                      .orElseThrow(() -> new RuntimeException("cannot find collection with name '" + name + "'"
                                                              + " in index '" + name + "'"));
  }

  public void setCollections(List<CollectionMetadata> collectionMetadata) {
    this.collections = collectionMetadata;
  }

  public boolean isValid() {
    return isValidSettingsResource()
           && collections.stream().anyMatch(CollectionMetadata::isValid);
  }

}
