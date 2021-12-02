package zone.cogni.asquare.cube.index;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

public class IndexMetadataService {

  private static final Logger log = LoggerFactory.getLogger(IndexMetadataService.class);

  private static final String indent = "        ";

  private final ApplicationContext resourcePatternResolver;
  private final String configurationClasspath;

  private List<IndexMetadata> indexesMetadata;
  private boolean initializationFailure;

  public IndexMetadataService(ApplicationContext resourcePatternResolver, String configurationClasspath) {
    this.resourcePatternResolver = resourcePatternResolver;
    this.configurationClasspath = calculateConfigurationClasspath(configurationClasspath);
  }

  /**
   * Predictable version of classpath used to find all configuration data.
   *
   * @param configurationClasspath classpath
   * @return configurationClasspath starting with <code>classpath:</code> and not ending with <code>/</code>
   */
  @Nonnull
  private String calculateConfigurationClasspath(@Nonnull String configurationClasspath) {
    if (!configurationClasspath.startsWith("classpath:"))
      throw new RuntimeException("please make sure classpath is explicit by starting with classpath:");

    return configurationClasspath.endsWith("/") ? configurationClasspath.substring(0, configurationClasspath.length() - 1)
                                                : configurationClasspath;
  }

  @PostConstruct
  public void init() {
    indexesMetadata = calculateIndexesMetadata();
    validate();

    if (initializationFailure)
      throw new RuntimeException("initialization failed: see logs for problems");
  }

  public List<IndexMetadata> getIndexesMetadata() {
    return indexesMetadata;
  }

  /**
   * @return list of indexes managed by service for current <code>elasticStore</code>
   */
  @Nonnull
  public List<String> getValidIndexNames() {
    return indexesMetadata.stream()
                          .filter(IndexMetadata::isValid)
                          .map(IndexMetadata::getName)
                          .collect(Collectors.toList());
  }

  private List<IndexMetadata> calculateIndexesMetadata() {
    Resource[] resources = getResources("/**/*");
    return getLocalPaths(resources).stream()
                                   .map(this::stripSlashAtFront)
                                   .filter(path -> path.contains("/")) // <- make sure it's a folder
                                   .map(path -> StringUtils.substringBefore(path, "/")) // <- take folder name
                                   .distinct()
                                   .sorted()
                                   .map(this::calculateIndexMetadata)
                                   .collect(Collectors.toList());
  }

  /**
   * @param resources for which local paths will be returned
   * @return paths after <code>configurationClasspath</code> as <code>String</code>
   */
  private List<String> getLocalPaths(@Nonnull Resource[] resources) {
    String prefix = StringUtils.substringAfter(configurationClasspath, "classpath:");
    return Arrays.stream(resources)
                 .map(r -> getLocalPath(prefix, r))
                 .collect(Collectors.toList());
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

  private IndexMetadata calculateIndexMetadata(String indexName) {
    IndexMetadata indexMetadata = new IndexMetadata();
    indexMetadata.setName(indexName);
    indexMetadata.setSettingsResource(calculateElasticSettingsResource(indexName));
    indexMetadata.setCollections(calculateCollectionsMetadata(indexMetadata));
    return indexMetadata;
  }

  private Resource calculateElasticSettingsResource(String indexName) {
    return getResource("/" + indexName + "/elastic-settings.json");
  }

  private List<CollectionMetadata> calculateCollectionsMetadata(IndexMetadata indexMetadata) {
    Resource[] resources = getResources("/" + indexMetadata.getName() + "/**/*");
    List<String> localPaths = getLocalPaths(resources);
    return localPaths.stream()
                     .map(this::stripSlashAtFront)
                     .map(path -> StringUtils.substringAfter(path, indexMetadata.getName() + "/")) // <- strip index
                     .filter(path -> path.contains("/")) // <- make sure it's a folder
                     .map(path -> StringUtils.substringBefore(path, "/")) // <- take folder name
                     .distinct()
                     .sorted()
                     .map(collectionName -> calculateCollectionMetadata(indexMetadata, collectionName))
                     .collect(Collectors.toList());
  }

  private String stripSlashAtFront(String path) {
    return path.startsWith("/") ? StringUtils.substringAfter(path, "/") : path;
  }

  private CollectionMetadata calculateCollectionMetadata(IndexMetadata indexMetadata, String collection) {
    String index = indexMetadata.getName();

    CollectionMetadata result = new CollectionMetadata();
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
    return resourcePatternResolver.getResource(configurationClasspath + path);
  }

  private Resource[] getResources(String path) {
    String locationPattern = configurationClasspath + path;
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
    log.info("'{}' index service", configurationClasspath);

    List<String> indexNames = getValidIndexNames();
    log.info("{} index count: {}", indent, indexNames.size());

    if (indexNames.isEmpty()) {
      log.error("{} no valid indexes configured", indent);
      initializationFailure = true;
    }

    if (isNotEmpty(indexNames)) log.info("{} valid indexes:   {}", indent, String.join(", ", indexNames));
    if (isNotEmpty(getInvalidIndexNames()))
      log.info("{} invalid indexes: {}", indent, String.join(", ", getInvalidIndexNames()));

    indexesMetadata.forEach(this::validateIndex);
  }

  @Nonnull
  private List<String> getInvalidIndexNames() {
    return indexesMetadata.stream()
                          .filter(indexMetadata -> !indexMetadata.isValid())
                          .map(IndexMetadata::getName)
                          .collect(Collectors.toList());
  }

  private void validateIndex(IndexMetadata indexMetadata) {
    // valid or not
    if (indexMetadata.isValid()) log.info("  '{}' index is valid", indexMetadata.getName());
    else {
      log.error("  '{}' index NOT is valid", indexMetadata.getName());
      initializationFailure = true;
    }

    // settings
    if (!indexMetadata.isValidSettingsResource()) {
      log.error("{}   elastic-settings.json is missing", indent);
      initializationFailure = true;
    }

    // collections
    List<String> collectionNames = getValidCollectionNames(indexMetadata);
    log.info("{}   collection count: {}", indent, collectionNames.size());

    if (collectionNames.isEmpty()) {
      log.error("{}   no valid collections configured", indent);
      initializationFailure = true;
    }

    if (isNotEmpty(collectionNames))
      log.info("{}   valid collections:   {}", indent, String.join(", ", collectionNames));
    if (isNotEmpty(getInvalidCollectionNames(indexMetadata)))
      log.info("{}   invalid collections: {}", indent, String.join(", ", getInvalidCollectionNames(indexMetadata)));

    // deeper check into collections
    indexMetadata.getCollections().forEach(this::validateCollection);
  }

  private List<String> getValidCollectionNames(IndexMetadata indexMetadata) {
    return indexMetadata.getValidCollections()
                        .stream()
                        .map(CollectionMetadata::getName)
                        .collect(Collectors.toList());
  }

  public List<String> getInvalidCollectionNames(IndexMetadata indexMetadata) {
    return indexMetadata.getCollections()
                        .stream()
                        .filter(collectionMetadata -> !collectionMetadata.isValid())
                        .map(CollectionMetadata::getName)
                        .collect(Collectors.toList());
  }

  private void validateCollection(CollectionMetadata collectionMetadata) {
    // valid or not
    if (collectionMetadata.isValid()) log.info("    '{}' collection is valid", collectionMetadata.getName());
    else {
      log.error("    '{}' collection NOT is valid", collectionMetadata.getName());
      initializationFailure = true;
    }

    // select
    if (collectionMetadata.getSelectQueryResources().isEmpty())
      log.error("{}     select-* queries are missing", indent);

    // construct
    if (collectionMetadata.getConstructQueryResources().isEmpty())
      log.error("{}     construct-* queries are missing", indent);

    // facet
    if (collectionMetadata.getFacetQueryResources().isEmpty()) log.warn("{}     facets/* queries are missing", indent);
  }

}
