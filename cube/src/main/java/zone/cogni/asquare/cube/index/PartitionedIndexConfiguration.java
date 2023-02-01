package zone.cogni.asquare.cube.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vavr.control.Try;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.stream.Collectors;

public class PartitionedIndexConfiguration {

  private String name;
  private Resource settingsResource;
  private List<PartitionConfiguration> partitions;

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

  public List<PartitionConfiguration> getPartitions() {
    return partitions;
  }

  public void setPartitions(List<PartitionConfiguration> partitionConfigurations) {
    this.partitions = partitionConfigurations;
  }

  public List<PartitionConfiguration> getValidPartitions() {
    return partitions.stream()
                     .filter(PartitionConfiguration::isValid)
                     .collect(Collectors.toList());
  }

  public PartitionConfiguration getValidPartition(String name) {
    return partitions.stream()
                     .filter(PartitionConfiguration::isValid)
                     .filter(partition -> partition.getName().equals(name))
                     .findFirst()
                     .orElseThrow(() -> new RuntimeException("cannot find partition with name '" + name + "'"
                                                             + " in index '" + name + "'"));
  }

  public boolean isValid() {
    return isValidSettingsResource()
           && partitions.stream().anyMatch(PartitionConfiguration::isValid);
  }

}
