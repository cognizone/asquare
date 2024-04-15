package zone.cogni.asquare.sparqlservice;

import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.springframework.core.io.FileSystemResource;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.libs.jena.utils.JenaUtils;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.util.function.Function;

public class RdfStoreSparqlService implements SparqlService {
  private final RdfStoreService rdfStoreService;

  public RdfStoreSparqlService(RdfStoreService rdfStoreService) {
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
    rdfStoreService.deleteGraph(graphUri);
  }
}
