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
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class IndexFolderService {

  private static final Logger log = LoggerFactory.getLogger(IndexFolderService.class);

  private static final String indent = "        ";

  private final ResourcePatternResolver resourcePatternResolver;
  private final String configurationPath;

  private List<IndexFolder> indexFolders;
  private boolean initializationFailure;

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

  @PostConstruct
  public void init() {
    indexFolders = calculateIndexFolders();
    validate();

    if (initializationFailure)
      throw new RuntimeException("initialization failed: see logs for problems");
  }

  public List<IndexFolder> getIndexFolders() {
    return indexFolders;
  }

  /**
   * @return list of indexes managed by service for current <code>elasticStore</code>
   */
  @Nonnull
  public List<String> getValidIndexNames() {
    return indexFolders.stream()
                       .filter(IndexFolder::isValid)
                       .map(IndexFolder::getName)
                       .collect(Collectors.toList());
  }

  private List<IndexFolder> calculateIndexFolders() {
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

  private IndexFolder calculateIndexFolder(String indexName) {
    IndexFolder indexFolder = new IndexFolder();
    indexFolder.setName(indexName);
    indexFolder.setSettingsResource(calculateElasticSettingsResource(indexName));
    indexFolder.setCollectionFolders(calculateCollectionFolders(indexFolder));
    return indexFolder;
  }

  private Resource calculateElasticSettingsResource(String indexName) {
    return getResource("/" + indexName + "/elastic-settings.json");
  }

  private List<CollectionFolder> calculateCollectionFolders(IndexFolder indexFolder) {
    Resource[] resources = getResources("/" + indexFolder.getName() + "/**/*");
    List<String> localPaths = getLocalPaths(resources);
    return localPaths.stream()
                     .map(this::stripSlashAtFront)
                     .map(path -> StringUtils.substringAfter(path, indexFolder.getName() + "/")) // <- strip index
                     .filter(path -> path.contains("/")) // <- make sure it's a folder
                     .map(path -> StringUtils.substringBefore(path, "/")) // <- take folder name
                     .distinct()
                     .sorted()
                     .map(collectionName -> calculateCollectionFolder(indexFolder, collectionName))
                     .collect(Collectors.toList());
  }

  private String stripSlashAtFront(String path) {
    return path.startsWith("/") ? StringUtils.substringAfter(path, "/") : path;
  }

  private CollectionFolder calculateCollectionFolder(IndexFolder indexFolder, String collection) {
    String index = indexFolder.getName();

    CollectionFolder result = new CollectionFolder();
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

  private void validate() {
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

    indexFolders.forEach(this::validateIndexFolder);
  }

  @Nonnull
  private List<String> getInvalidIndexNames() {
    return indexFolders.stream()
                       .filter(indexFolder -> !indexFolder.isValid())
                       .map(IndexFolder::getName)
                       .collect(Collectors.toList());
  }

  private void validateIndexFolder(IndexFolder indexFolder) {
    // valid or not
    if (indexFolder.isValid()) log.info("  '{}' index is valid", indexFolder.getName());
    else {
      log.error("  '{}' index NOT is valid", indexFolder.getName());
      initializationFailure = true;
    }

    // settings
    if (!indexFolder.isValidSettingsResource()) {
      log.error("{}   elastic-settings.json is missing", indent);
      initializationFailure = true;
    }

    // collections
    List<String> collectionNames = getValidCollectionNames(indexFolder);
    log.info("{}   collection count: {}", indent, collectionNames.size());

    if (collectionNames.isEmpty()) {
      log.error("{}   no valid collections configured", indent);
      initializationFailure = true;
    }

    if (isNotEmpty(collectionNames))
      log.info("{}   valid collections:   {}", indent, String.join(", ", collectionNames));
    if (isNotEmpty(getInvalidCollectionNames(indexFolder)))
      log.info("{}   invalid collections: {}", indent, String.join(", ", getInvalidCollectionNames(indexFolder)));

    // deeper check into collections
    indexFolder.getCollectionFolders().forEach(this::validateCollectionFolder);
  }

  private List<String> getValidCollectionNames(IndexFolder indexFolder) {
    return indexFolder.getValidCollectionFolders()
                      .stream()
                      .map(CollectionFolder::getName)
                      .collect(Collectors.toList());
  }

  public List<String> getInvalidCollectionNames(IndexFolder indexFolder) {
    return indexFolder.getCollectionFolders()
                      .stream()
                      .filter(collectionFolder -> !collectionFolder.isValid())
                      .map(CollectionFolder::getName)
                      .collect(Collectors.toList());
  }

  private void validateCollectionFolder(CollectionFolder collectionFolder) {
    // valid or not
    if (collectionFolder.isValid()) log.info("    '{}' collection is valid", collectionFolder.getName());
    else {
      log.error("    '{}' collection NOT is valid", collectionFolder.getName());
      initializationFailure = true;
    }

    // select
    if (collectionFolder.getSelectQueryResources().isEmpty())
      log.error("{}     select-* queries are missing", indent);

    // construct
    if (collectionFolder.getConstructQueryResources().isEmpty())
      log.error("{}     construct-* queries are missing", indent);

    // facet
    if (collectionFolder.getFacetQueryResources().isEmpty()) log.warn("{}     facets/* queries are missing", indent);
  }

}
