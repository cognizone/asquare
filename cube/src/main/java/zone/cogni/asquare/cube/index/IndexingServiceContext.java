package zone.cogni.asquare.cube.index;

import zone.cogni.asquare.cube.convertor.ModelToJsonConversion;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.service.elasticsearch.info.ElasticsearchMetadataService;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;

import java.util.Map;

abstract class IndexingServiceContext implements FolderBasedIndexingService {

  protected abstract SpelService getSpelService();

  protected abstract PaginatedQuery getPaginatedQuery();

  protected abstract IndexingConfiguration getIndexFolderService();

  protected abstract RdfStoreService getRdfStore();

  protected abstract Elasticsearch7Store getElasticStore();

  protected abstract ModelToJsonConversion getModelToJsonConversion();

  protected abstract Map<String, String> getQueryTemplateParameters();

  protected abstract ElasticsearchMetadataService getElasticsearchMetadataService();

}
