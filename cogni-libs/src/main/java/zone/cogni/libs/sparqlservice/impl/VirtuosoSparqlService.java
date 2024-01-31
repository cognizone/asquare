package zone.cogni.libs.sparqlservice.impl;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.StringEntity;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import zone.cogni.libs.core.utils.ApacheHttpClientUtils;
import zone.cogni.libs.sparqlservice.SparqlService;

/**
 * SparqlService implementation for Virtuoso, backed by Apache HttpClient.
 */
public class VirtuosoSparqlService implements SparqlService {

  private final Config config;

  public VirtuosoSparqlService(Config config) {
    this.config = config;
  }

  @Override
  public void uploadTtlFile(final File file) {
    try {
      URL ttlResourceUrl = file.toURI().toURL();
      final byte[] data = IOUtils.toByteArray(
          Objects.requireNonNull(ttlResourceUrl.openStream()));
      VirtuosoHelper.add(config, data, file.toURI().toString(), false);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Model queryForModel(String query) {
    return
        ApacheHttpClientUtils.executeConstruct(config.getUrl(), config.getUser(),
            config.getPassword(), query,
            config.isGraphCrudUseBasicAuth());
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    ApacheHttpClientUtils.executeAuthenticatedPostOrPut(config.getUrl(), config.getUser(),
        config.getPassword(), config.isGraphCrudUseBasicAuth(),
        new StringEntity(updateQuery,
            StandardCharsets.UTF_8), false, "application/sparql-update");
  }

  @Override
  public void upload(Model model, String graphUri) {
    upload(model, graphUri, false);
  }

  private void upload(Model model, String graphUri, boolean replace) {
    final StringWriter writer = new StringWriter();
    VirtuosoHelper.patchModel(model).write(writer, "ttl");
    try {
      VirtuosoHelper.add(config, writer.toString().getBytes(), graphUri, replace);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <R> R executeSelectQuery(String selectQuery, Function<ResultSet, R> resultHandler) {
    return
        ApacheHttpClientUtils.executeSelect(config.getUrl(), config.getUser(),
            config.getPassword(), selectQuery,
            config.isGraphCrudUseBasicAuth(), resultHandler);
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    return ApacheHttpClientUtils.executeAsk(config.getUrl(), config.getUser(),
        config.getPassword(), askQuery,
        config.isGraphCrudUseBasicAuth());
  }

  @Override
  public void dropGraph(String graphUri) {
    executeUpdateQuery("clear graph <" + graphUri + ">");
  }

  @Override
  public void replaceGraph(String graphUri, Model model) {
    upload(model, graphUri, true);
  }
}
