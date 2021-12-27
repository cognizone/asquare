package zone.cogni.asquare.cube.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vavr.control.Try;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.stream.Collectors;

class IndexFolder {

  private String name;
  private Resource settingsResource;
  private List<CollectionFolder> collectionFolders;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Resource getSettingsResource() {
    return settingsResource;
  }

  /**
   * Returns settings as JSON.
   *
   * @return <code>elastic-settings.json</code> in <code>index</code> folder as an <code>ObjectNode</code>
   */
  public ObjectNode getSettingsJson() {
    Resource resource = getSettingsResource();
    return (ObjectNode) Try.of(() -> new ObjectMapper().readTree(resource.getInputStream()))
                           .get();
  }

  public void setSettingsResource(Resource settingsResource) {
    this.settingsResource = settingsResource;
  }

  public boolean isValidSettingsResource() {
    return settingsResource != null && settingsResource.exists();
  }

  public List<CollectionFolder> getCollectionFolders() {
    return collectionFolders;
  }

  public List<CollectionFolder> getValidCollectionFolders() {
    return collectionFolders.stream()
                            .filter(CollectionFolder::isValid)
                            .collect(Collectors.toList());
  }

  public CollectionFolder getValidCollectionFolder(String name) {
    return collectionFolders.stream()
                            .filter(CollectionFolder::isValid)
                            .filter(collection -> collection.getName().equals(name))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("cannot find collection with name '" + name + "'"
                                                                    + " in index '" + name + "'"));
  }

  public void setCollectionFolders(List<CollectionFolder> collectionFolders) {
    this.collectionFolders = collectionFolders;
  }

  public boolean isValid() {
    return isValidSettingsResource()
           && collectionFolders.stream().anyMatch(CollectionFolder::isValid);
  }

}
