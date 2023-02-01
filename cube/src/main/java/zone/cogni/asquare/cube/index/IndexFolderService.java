package zone.cogni.asquare.cube.index;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class IndexFolderService {

  private static final Logger log = LoggerFactory.getLogger(IndexFolderService.class);

  private static final String indent = "        ";

  @Deprecated
  private final ResourcePatternResolver resourcePatternResolver;
  @Deprecated
  private final String configurationPath;

  private List<PartitionedIndexConfiguration> partitionedIndexConfigurations;
  private boolean initializationFailure;

  public IndexFolderService() {
    this.resourcePatternResolver = null;
    this.configurationPath = null;
  }

  @Deprecated
  public IndexFolderService(ResourcePatternResolver resourcePatternResolver, String configurationPath) {
    this.resourcePatternResolver = resourcePatternResolver;
    this.configurationPath = calculateConfigurationPath(configurationPath);
  }

  /**
   * Path without / at end which will contain all configuration data.
   *
   * @param configurationPath classpath
   * @return configurationPath path not ending with <code>/</code>
   */
  @Nonnull
  private String calculateConfigurationPath(@Nonnull String configurationPath) {
    return configurationPath.endsWith("/") ? configurationPath.substring(0, configurationPath.length() - 1)
                                           : configurationPath;
  }

  /**
   * Use {@link FromFolderBuilder} instead.
   */
  @PostConstruct
  @Deprecated
  public void init() {
    if (resourcePatternResolver == null && configurationPath == null) {
      log.info("No configuration path set. Please make sure you invoke validate method explicitly.");
      return;
    }

    IndexFolderService fromBuilder = new FromFolderBuilder().withConfigurationPath(configurationPath)
                                                            .withResourcePatternResolver(resourcePatternResolver)
                                                            .build();

    partitionedIndexConfigurations = fromBuilder.getPartitionedIndexConfigurations();
    validate();
  }

  /**
   * Use #getPartitionedIndexConfigurations instead.
   */
  @Deprecated
  public List<PartitionedIndexConfiguration> getIndexFolders() {
    return Collections.unmodifiableList(partitionedIndexConfigurations);
  }

  public List<PartitionedIndexConfiguration> getPartitionedIndexConfigurations() {
    return Collections.unmodifiableList(partitionedIndexConfigurations);
  }

  public void setPartitionedIndexConfigurations(List<PartitionedIndexConfiguration> partitionedIndexConfigurations) {
    this.partitionedIndexConfigurations = partitionedIndexConfigurations;
  }

  /**
   * @return list of indexes managed by service for current <code>elasticStore</code>
   */
  @Nonnull
  public List<String> getValidIndexNames() {
    return partitionedIndexConfigurations.stream()
                                         .filter(PartitionedIndexConfiguration::isValid)
                                         .map(PartitionedIndexConfiguration::getName)
                                         .collect(Collectors.toList());
  }

  /**
   * Analyze all configurations and log results.
   *
   * @throws RuntimeException if no valid indexes are configured
   */
  public void validate() {
    log.info("'{}' index service", configurationPath);

    List<String> indexNames = getValidIndexNames();
    log.info("{} index count: {}", indent, indexNames.size());

    if (indexNames.isEmpty()) {
      log.error("{} no valid indexes configured", indent);
      initializationFailure = true;
    }

    if (isNotEmpty(indexNames)) log.info("{} valid indexes:   {}", indent, String.join(", ", indexNames));
    if (isNotEmpty(getInvalidIndexNames()))
      log.info("{} invalid indexes: {}", indent, String.join(", ", getInvalidIndexNames()));

    partitionedIndexConfigurations.forEach(this::validateIndexFolder);

    if (initializationFailure)
      throw new RuntimeException("initialization failed: see logs for problems");
  }

  @Nonnull
  private List<String> getInvalidIndexNames() {
    return partitionedIndexConfigurations.stream()
                                         .filter(indexFolder -> !indexFolder.isValid())
                                         .map(PartitionedIndexConfiguration::getName)
                                         .collect(Collectors.toList());
  }

  private void validateIndexFolder(PartitionedIndexConfiguration partitionedIndexConfiguration) {
    // valid or not
    if (partitionedIndexConfiguration.isValid())
      log.info("  '{}' index is valid", partitionedIndexConfiguration.getName());
    else {
      log.error("  '{}' index NOT is valid", partitionedIndexConfiguration.getName());
      initializationFailure = true;
    }

    // settings
    if (!partitionedIndexConfiguration.isValidSettingsResource()) {
      log.error("{}   elastic-settings.json is missing", indent);
      initializationFailure = true;
    }

    // collections
    List<String> collectionNames = getValidCollectionNames(partitionedIndexConfiguration);
    log.info("{}   collection count: {}", indent, collectionNames.size());

    if (collectionNames.isEmpty()) {
      log.error("{}   no valid collections configured", indent);
      initializationFailure = true;
    }

    if (isNotEmpty(collectionNames))
      log.info("{}   valid collections:   {}", indent, String.join(", ", collectionNames));
    if (isNotEmpty(getInvalidCollectionNames(partitionedIndexConfiguration)))
      log.info("{}   invalid collections: {}", indent, String.join(", ", getInvalidCollectionNames(partitionedIndexConfiguration)));

    // deeper check into collections
    partitionedIndexConfiguration.getPartitions().forEach(this::validateCollectionFolder);
  }

  private List<String> getValidCollectionNames(PartitionedIndexConfiguration partitionedIndexConfiguration) {
    return partitionedIndexConfiguration.getValidPartitions()
                                        .stream()
                                        .map(PartitionConfiguration::getName)
                                        .collect(Collectors.toList());
  }

  public List<String> getInvalidCollectionNames(PartitionedIndexConfiguration partitionedIndexConfiguration) {
    return partitionedIndexConfiguration.getPartitions()
                                        .stream()
                                        .filter(collectionFolder -> !collectionFolder.isValid())
                                        .map(PartitionConfiguration::getName)
                                        .collect(Collectors.toList());
  }

  private void validateCollectionFolder(PartitionConfiguration partitionConfiguration) {
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

    public IndexFolderService build() {
      return new IndexFolderService(resourcePatternResolver, configurationPath);
    }

    private List<PartitionedIndexConfiguration> calculateIndexFolders() {
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

    private PartitionedIndexConfiguration calculateIndexFolder(String indexName) {
      PartitionedIndexConfiguration partitionedIndexConfiguration = new PartitionedIndexConfiguration();
      partitionedIndexConfiguration.setName(indexName);
      partitionedIndexConfiguration.setSettingsResource(calculateElasticSettingsResource(indexName));
      partitionedIndexConfiguration.setPartitions(calculateCollectionFolders(partitionedIndexConfiguration));
      return partitionedIndexConfiguration;
    }

    private Resource calculateElasticSettingsResource(String indexName) {
      return getResource("/" + indexName + "/elastic-settings.json");
    }

    private List<PartitionConfiguration> calculateCollectionFolders(PartitionedIndexConfiguration partitionedIndexConfiguration) {
      Resource[] resources = getResources("/" + partitionedIndexConfiguration.getName() + "/**/*");
      List<String> localPaths = getLocalPaths(resources);
      return localPaths.stream()
                       .map(this::stripSlashAtFront)
                       .map(path -> StringUtils.substringAfter(path, partitionedIndexConfiguration.getName() + "/")) // <- strip index
                       .filter(path -> path.contains("/")) // <- make sure it's a folder
                       .map(path -> StringUtils.substringBefore(path, "/")) // <- take folder name
                       .distinct()
                       .sorted()
                       .map(collectionName -> calculateCollectionFolder(partitionedIndexConfiguration, collectionName))
                       .collect(Collectors.toList());
    }

    private PartitionConfiguration calculateCollectionFolder(PartitionedIndexConfiguration partitionedIndexConfiguration, String collection) {
      String index = partitionedIndexConfiguration.getName();

      PartitionConfiguration result = new PartitionConfiguration();
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
