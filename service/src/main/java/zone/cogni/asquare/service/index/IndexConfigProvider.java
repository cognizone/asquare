package zone.cogni.asquare.service.index;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.springframework.core.io.FileSystemResource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.service.elasticsearch.ElasticStore;
import zone.cogni.asquare.service.elasticsearch.v7.Elasticsearch7Store;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.virtuoso.SparqlRdfStoreService;
import zone.cogni.libs.jena.utils.JenaUtils;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.util.function.Function;

public class IndexConfigProvider {

  private final RdfStoreService rdfStoreService;
  private final SparqlService sparqlService;
  private final Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier;
  private final ElasticStore elasticStore;
  private final Function<ResourceIndex, Function<TypedResource, ObjectNode>> facetConversionSupplier;

  public IndexConfigProvider(RdfStoreService rdfStoreService,
                             Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier,
                             Elasticsearch7Store elasticStore,
                             Function<ResourceIndex, Function<TypedResource, ObjectNode>> facetConversionSupplier) {
    this.rdfStoreService = rdfStoreService;
    this.sparqlService = new SparqlServiceImpl(rdfStoreService);
    this.elasticStore = elasticStore;
    this.applicationProfileSupplier = applicationProfileSupplier;
    this.facetConversionSupplier = facetConversionSupplier;
  }

  public IndexConfigProvider(RdfStoreService rdfStoreService,
                             Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier,
                             Elasticsearch7Store elasticStore) {
    this(rdfStoreService, applicationProfileSupplier, elasticStore, null);
  }

  public IndexConfigProvider(SparqlService sparqlService,
                             Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier,
                             Elasticsearch7Store elasticStore) {
    this(new SparqlRdfStoreService(sparqlService), applicationProfileSupplier, elasticStore, null);
  }

  public IndexConfigProvider(SparqlService sparqlService,
                             Function<ResourceIndex, ApplicationProfile> applicationProfileSupplier,
                             Elasticsearch7Store elasticStore,
                             Function<ResourceIndex, Function<TypedResource, ObjectNode>> facetConversionSupplier) {
    this(new SparqlRdfStoreService(sparqlService), applicationProfileSupplier, elasticStore, facetConversionSupplier);
  }

  public RdfStoreService getRdfStoreService() {
    return rdfStoreService;
  }

  public SparqlService getSparqlService() {
    return sparqlService;
  }

  public Function<ResourceIndex, ApplicationProfile> getApplicationProfileSupplier() {
    return applicationProfileSupplier;
  }

  public Function<ResourceIndex, Function<TypedResource, ObjectNode>> getFacetConversionSupplier() {
    return facetConversionSupplier;
  }

  public ElasticStore getElasticStore() {
    return elasticStore;
  }

  public static class SparqlServiceImpl implements SparqlService {
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
