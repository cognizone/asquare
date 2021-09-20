package zone.cogni.libs.sparqlservice.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.function.Function;

public class FusekiSparqlService implements SparqlService {


  private final String sparqlUrl;

  public FusekiSparqlService(Config config) {
    this.sparqlUrl = config.getUrl();
  }

  @Override
  public void uploadTtlFile(File file) {
    try {
      String sparqlUrl = this.sparqlUrl + "/data?graph=" + StringUtils.removeEnd(file.getName(), ".ttl");

      Response response = Request.Post(sparqlUrl)
          .setHeader("Content-Type", "application/x-turtle;charset=utf-8")
          .bodyFile(file, ContentType.create("application/x-turtle"))
          .execute();
      StatusLine statusLine = response.returnResponse().getStatusLine();
      if ((statusLine.getStatusCode() / 100) != 2) {
        throw new RuntimeException("Upload didn't answer 2xx code " + statusLine);
      }
      response.discardContent();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public Model queryForModel(String query) {
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(sparqlUrl + "/query", query)) {
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    try {
      Response response = Request
          .Post(sparqlUrl + "/update")
          .body(new UrlEncodedFormEntity(Collections.singletonList(new BasicNameValuePair("update", updateQuery)), StandardCharsets.UTF_8))
          .execute();
      StatusLine statusLine = response.returnResponse().getStatusLine();
      if (statusLine.getStatusCode() != 200) {
        throw new RuntimeException("Update didn't answer 200 code " + statusLine);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void upload(Model model, String graphUri) {
    String insertUrl = this.sparqlUrl + "/data?graph=" + graphUri;
    StringWriter writer = new StringWriter();
    try {
      model.write(writer, "ttl");
      Response response = Request.Post(insertUrl)
          .setHeader("Content-Type", "application/x-turtle;charset=utf-8")
          .bodyByteArray(writer.toString().getBytes(), ContentType.create("application/x-turtle"))
          .execute();
      StatusLine statusLine = response.returnResponse().getStatusLine();
      if ((statusLine.getStatusCode() / 100) != 2) {
        throw new RuntimeException("Upload didn't answer 2xx code " + statusLine);
      }
      response.discardContent();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(sparqlUrl + "/query", query)) {
      return resultHandler.apply(queryExecution.execSelect());
    }
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(sparqlUrl, askQuery)) {
      return queryExecution.execAsk();
    }
  }

  @Override
  public void dropGraph(String graphUri) {
    executeUpdateQuery("drop graph <" + graphUri + ">");
  }
}
