package zone.cogni.asquare.cube.index;

import zone.cogni.asquare.cube.convertor.ModelToJsonConversion;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadata;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadataService;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract class IndexingServiceContext implements FolderBasedIndexingService {

  private final ElasticsearchMetadataService elasticsearchMetadataService = new ElasticsearchMetadataService(new ElasticsearchMetadata.Configuration());

  protected abstract SpelService getSpelService();

  protected abstract PaginatedQuery getPaginatedQuery();

  protected abstract IndexFolderService getIndexFolderService();

  protected abstract RdfStoreService getRdfStore();

  protected abstract Elasticsearch7Store getElasticStore();

  protected abstract ModelToJsonConversion getModelToJsonConversion();

  protected abstract Map<String, String> getQueryTemplateParameters();

  protected ElasticsearchMetadataService getElasticsearchMetadataService() {
    return elasticsearchMetadataService;
  }

  @Override
  public void ensureIndexExists(@Nonnull String index) {
    if (existsIndex(index)) {
      return;
    }

    List<IndexFolder> indexFolder = getIndexFolderService().getIndexFolders().stream().filter(folder -> folder.getName().equals(index)).collect(Collectors.toList());

    if (indexFolder.isEmpty()) {
      throw new RuntimeException("No index folder found for index " + index);
    }

    getElasticStore().createIndex(index, indexFolder.get(0).getSettingsJson());
  }

  private boolean existsIndex(String index) {
    ElasticsearchMetadata metadata = getElasticsearchMetadataService().getElasticsearchMetadata(getElasticStore());
    return metadata.getIndexes()
                   .stream()
                   .map(ElasticsearchMetadata.Index::getName)
                   .anyMatch(index::equals);
  }

}
