package zone.cogni.asquare.cube.index.swap;

import org.apache.commons.collections4.CollectionUtils;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadata;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An alias can be used over multiple indexes; an index prefix identifies a group of indexes
 */
public class IndexSwapState {

  /**
   * Name of alias used in swapping process
   */
  private String aliasName;

  /**
   * An index prefix identifies a group of indexes, one of which should be active.
   */
  private String indexPrefix;

  private List<ElasticsearchMetadata.Index> indexesMatchingAlias;
  private List<ElasticsearchMetadata.Index> indexesMatchingPrefix;

  /**
   * Currently active index, must match alias and index prefix.
   */
  private ElasticsearchMetadata.Index indexMatchingAliasAndPrefix;

  /**
   * Future active index.
   */
  private String newIndexName;

  public String getAliasName() {
    return aliasName;
  }

  void setAliasName(String aliasName) {
    this.aliasName = aliasName;
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  void setIndexPrefix(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public List<ElasticsearchMetadata.Index> getIndexesMatchingAlias() {
    return indexesMatchingAlias;
  }

  void setIndexesMatchingAlias(List<ElasticsearchMetadata.Index> indexesMatchingAlias) {
    this.indexesMatchingAlias = indexesMatchingAlias;
  }

  public List<ElasticsearchMetadata.Index> getIndexesMatchingPrefix() {
    return indexesMatchingPrefix;
  }

  void setIndexesMatchingPrefix(List<ElasticsearchMetadata.Index> indexesMatchingPrefix) {
    this.indexesMatchingPrefix = indexesMatchingPrefix;
  }

  public boolean hasIndexMatchingAliasAndPrefix() {
    return indexMatchingAliasAndPrefix != null;
  }

  public ElasticsearchMetadata.Index getIndexMatchingAliasAndPrefix() {
    return indexMatchingAliasAndPrefix;
  }

  void setIndexMatchingAliasAndPrefix(ElasticsearchMetadata.Index indexMatchingAliasAndPrefix) {
    this.indexMatchingAliasAndPrefix = indexMatchingAliasAndPrefix;
  }

  public String getNewIndexName() {
    return newIndexName;
  }

  void setNewIndexName(String newIndexName) {
    this.newIndexName = newIndexName;
  }

  void configure() {
    Objects.requireNonNull(aliasName);
    Objects.requireNonNull(indexPrefix);
    Objects.requireNonNull(indexesMatchingAlias);
    Objects.requireNonNull(indexesMatchingPrefix);

    Collection<ElasticsearchMetadata.Index> activeIndexes = CollectionUtils.intersection(indexesMatchingAlias,
                                                                                         indexesMatchingPrefix);

    if (activeIndexes.size() > 1) {
      String actives = activeIndexes.stream()
                                    .map(ElasticsearchMetadata.Index::getName)
                                    .collect(Collectors.joining(", "));
      throw new RuntimeException("multiple active indexes, this should not happen: " + actives);
    }

    if (!activeIndexes.isEmpty())
      this.indexMatchingAliasAndPrefix = (activeIndexes.stream().findFirst().get());

    this.newIndexName = indexPrefix + "-" + System.currentTimeMillis();
  }
}
