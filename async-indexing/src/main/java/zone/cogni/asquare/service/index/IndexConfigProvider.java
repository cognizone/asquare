package zone.cogni.asquare.service.index;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.springframework.core.io.FileSystemResource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.sparqlservice.RdfStoreSparqlService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.virtuoso.SparqlRdfStoreService;
import zone.cogni.libs.jena.utils.JenaUtils;
import zone.cogni.libs.sparqlservice.SparqlService;

import javax.annotation.Nullable;
import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IndexConfigProvider {

  private final RdfStoreService rdfStoreService;
  private final Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier;
  private final ElasticStore elasticStore;
  @Nullable
  private final Function<ResourceIndex, Function<TypedResource, ObjectNode>> facetConversionSupplier;
  /**
   * Post index interceptor consuming the resulting index document and receiving the Type of the object.
   * This allows to tweak the index document before it's sent to elasticsearch.
   */
  @Nullable
  private final BiConsumer<ObjectNode, String> postIndexInterceptor;
  @Getter(lazy = true)
  private final SparqlService sparqlService = new RdfStoreSparqlService(rdfStoreService); //init will be done on first get call

  /**
   * @deprecated Use builder instead.
   */
  @Deprecated
  public IndexConfigProvider(RdfStoreService rdfStoreService,
                             Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier,
                             Elasticsearch7Store elasticStore,
                             Function<ResourceIndex, Function<TypedResource, ObjectNode>> facetConversionSupplier) {
    this(rdfStoreService, applicationProfileSupplier, elasticStore, facetConversionSupplier, null);
  }

  /**
   * @deprecated Use builder instead.
   */
  @Deprecated
  public IndexConfigProvider(RdfStoreService rdfStoreService,
                             Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier,
                             Elasticsearch7Store elasticStore) {
    this(rdfStoreService, applicationProfileSupplier, elasticStore, null, null);
  }

  /**
   * @deprecated Use builder instead.
   */
  @Deprecated
  public IndexConfigProvider(SparqlService sparqlService,
                             Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier,
                             Elasticsearch7Store elasticStore) {
    this(new SparqlRdfStoreService(sparqlService), applicationProfileSupplier, elasticStore, null, null);
  }

  /**
   * @deprecated Use builder instead.
   */
  @Deprecated
  public IndexConfigProvider(SparqlService sparqlService,
                             Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier,
                             Elasticsearch7Store elasticStore,
                             Function<ResourceIndex, Function<TypedResource, ObjectNode>> facetConversionSupplier) {
    this(new SparqlRdfStoreService(sparqlService), applicationProfileSupplier, elasticStore, facetConversionSupplier, null);
  }

  /**
   * @deprecated Use {@link RdfStoreSparqlService}
   */
  @Deprecated
  static class SparqlServiceImpl implements SparqlService {
    private final RdfStoreService rdfStoreService;

    public SparqlServiceImpl(RdfStoreService rdfStoreService) {
      this.rdfStoreService = rdfStoreService;
    }

    @Override
    public void uploadTtlFile(File file) {
      Model model = JenaUtils.read(new FileSystemResource(file));
      rdfStoreService.addData(model);
    }

    @Override
    public Model queryForModel(String query) {
      return rdfStoreService.executeConstructQuery(query);
    }

    @Override
    public void executeUpdateQuery(String updateQuery) {
      rdfStoreService.executeUpdateQuery(updateQuery);
    }

    @Override
    public boolean executeAskQuery(String updateQuery) {
      return rdfStoreService.executeAskQuery(updateQuery);
    }

    @Override
    public void upload(Model model, String graphUri) {
      rdfStoreService.addData(model);
    }

    @Override
    public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
      return rdfStoreService.executeSelectQuery(query, resultHandler::apply);
    }

    @Override
    public void dropGraph(String graphUri) {
      rdfStoreService.executeUpdateQuery("clear graph <" + graphUri + ">");
    }
  }
}
