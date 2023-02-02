package zone.cogni.asquare.cube.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import zone.cogni.core.spring.ResourceHelper;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class IndexingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(IndexingConfiguration.class);

  private static final String indent = "        ";

  private List<IndexingConfiguration.Index> indexConfigurations;
  private Boolean initializationFailure;

  public List<IndexingConfiguration.Index> getIndexConfigurations() {
    return Collections.unmodifiableList(indexConfigurations);
  }

  public void setIndexConfigurations(List<IndexingConfiguration.Index> indexConfigurations) {
    this.indexConfigurations = indexConfigurations;
  }

  /**
   * @return list of indexes managed by service for current <code>elasticStore</code>
   */
  @Nonnull
  public List<String> getValidIndexNames() {
    return indexConfigurations.stream()
                              .filter(IndexingConfiguration.Index::isValid)
                              .map(IndexingConfiguration.Index::getName)
                              .collect(Collectors.toList());
  }

  /**
   * Analyze all configurations and log results.
   *
   * @throws RuntimeException if no valid indexes are configured
   */
  public void validate() {
    if (initializationFailure != null) return;

    List<String> indexNames = getValidIndexNames();
    log.info("{} index count: {}", indent, indexNames.size());

    if (indexNames.isEmpty()) {
      log.error("{} no valid indexes configured", indent);
      initializationFailure = true;
    }

    if (isNotEmpty(indexNames)) log.info("{} valid indexes:   {}", indent, String.join(", ", indexNames));
    if (isNotEmpty(getInvalidIndexNames()))
      log.info("{} invalid indexes: {}", indent, String.join(", ", getInvalidIndexNames()));

    indexConfigurations.forEach(this::validateIndexConfiguration);

    if (initializationFailure)
      throw new RuntimeException("initialization failed: see logs for problems");
  }

  @Nonnull
  private List<String> getInvalidIndexNames() {
    return indexConfigurations.stream()
                              .filter(indexFolder -> !indexFolder.isValid())
                              .map(IndexingConfiguration.Index::getName)
                              .collect(Collectors.toList());
  }

  private void validateIndexConfiguration(IndexingConfiguration.Index indexConfiguration) {
    // valid or not
    if (indexConfiguration.isValid())
      log.info("  '{}' index is valid", indexConfiguration.getName());
    else {
      log.error("  '{}' index NOT is valid", indexConfiguration.getName());
      initializationFailure = true;
    }

    // settings
    if (!indexConfiguration.isValidSettingsResource()) {
      log.error("{}   elastic-settings.json is missing", indent);
      initializationFailure = true;
    }

    // collections
    List<String> collectionNames = getValidCollectionNames(indexConfiguration);
    log.info("{}   collection count: {}", indent, collectionNames.size());

    if (collectionNames.isEmpty()) {
      log.error("{}   no valid collections configured", indent);
      initializationFailure = true;
    }

    if (isNotEmpty(collectionNames))
      log.info("{}   valid collections:   {}", indent, String.join(", ", collectionNames));
    if (isNotEmpty(getInvalidCollectionNames(indexConfiguration)))
      log.info("{}   invalid collections: {}", indent, String.join(", ", getInvalidCollectionNames(indexConfiguration)));

    // deeper check into collections
    indexConfiguration.getPartitions().forEach(this::validateCollectionFolder);
  }

  private List<String> getValidCollectionNames(IndexingConfiguration.Index indexConfiguration) {
    return indexConfiguration.getValidPartitions()
                             .stream()
                             .map(IndexingConfiguration.Partition::getName)
                             .collect(Collectors.toList());
  }

  public List<String> getInvalidCollectionNames(IndexingConfiguration.Index indexConfiguration) {
    return indexConfiguration.getPartitions()
                             .stream()
                             .filter(collectionFolder -> !collectionFolder.isValid())
                             .map(IndexingConfiguration.Partition::getName)
                             .collect(Collectors.toList());
  }

  private void validateCollectionFolder(IndexingConfiguration.Partition partitionConfiguration) {
    // valid or not
    if (partitionConfiguration.isValid()) log.info("    '{}' collection is valid", partitionConfiguration.getName());
    else {
      log.error("    '{}' collection NOT is valid", partitionConfiguration.getName());
      initializationFailure = true;
    }

    // select
    if (partitionConfiguration.getSelectQueryResources().isEmpty())
      log.error("{}     select-* queries are missing", indent);

    // construct
    if (partitionConfiguration.getConstructQueryResources().isEmpty())
      log.error("{}     construct-* queries are missing", indent);

    // facet
    if (partitionConfiguration.getFacetQueryResources().isEmpty())
      log.warn("{}     facets/* queries are missing", indent);
  }

  public static class Index {

    private String name;
    private Resource settingsResource;
    private List<Partition> partitions;

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

    public List<Partition> getPartitions() {
      return partitions;
    }

    public void setPartitions(List<Partition> partitionConfigurations) {
      this.partitions = partitionConfigurations;
    }

    public List<Partition> getValidPartitions() {
      return partitions.stream()
                       .filter(Partition::isValid)
                       .collect(Collectors.toList());
    }

    public Partition getValidPartition(String name) {
      return partitions.stream()
                       .filter(Partition::isValid)
                       .filter(partition -> partition.getName().equals(name))
                       .findFirst()
                       .orElseThrow(() -> new RuntimeException("cannot find partition with name '" + name + "'"
                                                               + " in index '" + name + "'"));
    }

    public boolean isValid() {
      return isValidSettingsResource()
             && partitions.stream().anyMatch(Partition::isValid);
    }

  }

  public static class Partition {

    private String name;
    private List<Resource> selectQueryResources;
    private List<String> selectQueries;
    private List<Resource> constructQueryResources;
    private List<String> constructQueries;
    private List<Resource> facetQueryResources;
    private List<String> facetQueries;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<Resource> getSelectQueryResources() {
      return selectQueryResources;
    }

    public void setSelectQueryResources(List<Resource> selectQueryResources) {
      this.selectQueryResources = selectQueryResources;
      selectQueries = getQueries(this.selectQueryResources);
    }

    public List<String> getSelectQueries() {
      return selectQueries;
    }

    public List<Resource> getConstructQueryResources() {
      return constructQueryResources;
    }

    public void setConstructQueryResources(List<Resource> constructQueryResources) {
      this.constructQueryResources = constructQueryResources;
      constructQueries = getQueries(this.constructQueryResources);
    }

    public List<String> getConstructQueries() {
      return constructQueries;
    }

    public List<Resource> getFacetQueryResources() {
      return facetQueryResources;
    }

    public void setFacetQueryResources(List<Resource> facetQueryResources) {
      this.facetQueryResources = facetQueryResources;
      facetQueries = getQueries(this.facetQueryResources);
    }

    public List<String> getFacetQueries() {
      return facetQueries;
    }

    private static List<String> getQueries(List<Resource> queryResources) {
      return queryResources.stream()
                           .map(ResourceHelper::toString)
                           .collect(Collectors.toList());
    }

    public boolean isValid() {
      return !selectQueryResources.isEmpty() && !constructQueryResources.isEmpty();
    }
  }

  public static class FromFolderBuilder {
    private ResourcePatternResolver resourcePatternResolver;
    private String configurationPath;

    public FromFolderBuilder withResourcePatternResolver(ResourcePatternResolver resourcePatternResolver) {
      this.resourcePatternResolver = resourcePatternResolver;
      return this;
    }

    public FromFolderBuilder withConfigurationPath(String configurationPath) {
      this.configurationPath = configurationPath;
      return this;
    }

    public IndexingConfiguration build() {
      // for now only to make old code work!
      IndexingConfiguration indexingConfiguration = new IndexFolderService();
      indexingConfiguration.setIndexConfigurations(calculateIndexFolders());
      indexingConfiguration.validate();

      return indexingConfiguration;
    }

    private List<IndexingConfiguration.Index> calculateIndexFolders() {
      Resource[] resources = getResources("/**/*");
      return getLocalPaths(resources).stream()
                                     .map(this::stripSlashAtFront)
                                     .filter(path -> path.contains("/")) // <- make sure it's a folder
                                     .map(path -> StringUtils.substringBefore(path, "/")) // <- take folder name
                                     .distinct()
                                     .sorted()
                                     .map(this::calculateIndexFolder)
                                     .collect(Collectors.toList());
    }

    /**
     * @param resources for which local paths will be returned
     * @return paths after <code>configurationClasspath</code> as <code>String</code>
     */
    private List<String> getLocalPaths(@Nonnull Resource[] resources) {
      String prefix = getLocalPathPrefix();
      return Arrays.stream(resources)
                   .map(r -> getLocalPath(prefix, r))
                   .collect(Collectors.toList());
    }

    private String getLocalPathPrefix() {
      String result;
      if (configurationPath.startsWith("classpath:"))
        result = StringUtils.substringAfter(configurationPath, "classpath:");
      else if (configurationPath.startsWith("file:"))
        result = StringUtils.substringAfter(configurationPath, "file:");
      else
        throw new RuntimeException("Unknown path prefix, should it be supported? path is '" + configurationPath + "'.");

      return File.separatorChar != '/' ? result.replace(File.separatorChar, '/') : result;
    }

    /**
     * @param prefix   <code>configurationClasspath</code> without <code>classpath:</code>
     * @param resource for which local path will be returned
     * @return path after <code>configurationClasspath</code> as <code>String</code>
     */
    @Nonnull
    private String getLocalPath(@Nonnull String prefix, @Nonnull Resource resource) {
      try {
        if (resource instanceof ClassPathResource) {
          String path = ((ClassPathResource) resource).getPath();
          return StringUtils.substringAfterLast(path, prefix);
        }
        else if (resource instanceof FileSystemResource) {
          // !! when running on a local environment classpaths can resolve to FileSystemResource :(
          String path = resource.getFile().getPath();
          // make sure slash is always of form /
          String slashPath = File.separatorChar != '/' ? path.replace(File.separatorChar, '/') : path;
          return StringUtils.substringAfterLast(slashPath, prefix);
        }
        else {
          throw new RuntimeException("local path not (yet) supported for resource of type " + resource.getClass()
                                                                                                      .getName());
        }
      }
      catch (IOException e) {
        throw new RuntimeException("problem getting path for resource of type " + resource.getClass().getName()
                                   + " and name " + resource.getFilename(), e);
      }
    }

    private String stripSlashAtFront(String path) {
      return path.startsWith("/") ? StringUtils.substringAfter(path, "/") : path;
    }

    private IndexingConfiguration.Index calculateIndexFolder(String indexName) {
      IndexingConfiguration.Index indexConfiguration = new IndexingConfiguration.Index();
      indexConfiguration.setName(indexName);
      indexConfiguration.setSettingsResource(calculateElasticSettingsResource(indexName));
      indexConfiguration.setPartitions(calculateCollectionFolders(indexConfiguration));
      return indexConfiguration;
    }

    private Resource calculateElasticSettingsResource(String indexName) {
      return getResource("/" + indexName + "/elastic-settings.json");
    }

    private List<IndexingConfiguration.Partition> calculateCollectionFolders(IndexingConfiguration.Index indexConfiguration) {
      Resource[] resources = getResources("/" + indexConfiguration.getName() + "/**/*");
      List<String> localPaths = getLocalPaths(resources);
      return localPaths.stream()
                       .map(this::stripSlashAtFront)
                       .map(path -> StringUtils.substringAfter(path, indexConfiguration.getName() + "/")) // <- strip index
                       .filter(path -> path.contains("/")) // <- make sure it's a folder
                       .map(path -> StringUtils.substringBefore(path, "/")) // <- take folder name
                       .distinct()
                       .sorted()
                       .map(collectionName -> calculateCollectionFolder(indexConfiguration, collectionName))
                       .collect(Collectors.toList());
    }

    private IndexingConfiguration.Partition calculateCollectionFolder(IndexingConfiguration.Index indexConfiguration, String collection) {
      String index = indexConfiguration.getName();

      IndexingConfiguration.Partition result = new IndexingConfiguration.Partition();
      result.setName(collection);
      result.setSelectQueryResources(getCollectionSelectQueries(index, collection));
      result.setConstructQueryResources(getCollectionConstructQueries(index, collection));
      result.setFacetQueryResources(getFacetQueries(index, collection));
      return result;
    }

    private List<Resource> getCollectionSelectQueries(String index, String collection) {
      return Arrays.asList(getResources("/" + index + "/" + collection + "/select-*.*"));
    }

    private List<Resource> getCollectionConstructQueries(String index, String collection) {
      return Arrays.asList(getResources("/" + index + "/" + collection + "/construct-*.*"));
    }

    private List<Resource> getFacetQueries(@Nonnull String index, @Nonnull String collection) {
      return Arrays.asList(getResources("/" + index + "/" + collection + "/facets/*sparql*"));
    }

    private Resource getResource(String path) {
      return resourcePatternResolver.getResource(configurationPath + path);
    }

    private Resource[] getResources(String path) {
      String locationPattern = configurationPath + path;
      try {
        return resourcePatternResolver.getResources(locationPattern);
      }
      catch (FileNotFoundException e) {
        return new Resource[0];
      }
      catch (IOException e) {
        throw new RuntimeException("unable to resolve resources for '" + locationPattern + "'");
      }
    }
  }

}
