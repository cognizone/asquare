package zone.cogni.libs.sparqlservice.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;
import zone.cogni.libs.jena.utils.JenaUtils;
import zone.cogni.libs.jena.utils.TripleSerializationFormat;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.function.Function;

import static zone.cogni.libs.sparqlservice.impl.HttpHelper.createAuthenticationHttpContext;
import static zone.cogni.libs.sparqlservice.impl.HttpHelper.executeAndConsume;

public class StardogSparqlService implements SparqlService {
  private final String endpointUrl;
  private final Header authHeader;
  private final HttpClientContext authenticationHttpContext;

  public StardogSparqlService(Config config) {
    endpointUrl = config.getUrl();
    if (StringUtils.isNoneBlank(config.getUser(), config.getPassword())) {
      String authEncoded = Base64.getEncoder().encodeToString((config.getUser() + ":" + config.getPassword()).getBytes(StandardCharsets.UTF_8));
      authHeader = new BasicHeader("Authorization", "Basic " + authEncoded);
      authenticationHttpContext = createAuthenticationHttpContext(config.getUser(), config.getPassword());
    }
    else {
      authHeader = null; //we're allowed to send null value to the "setHeader" method (whop whop)
      authenticationHttpContext = null;
    }
  }

  @Override
  public void uploadTtlFile(File file) {
    Request request = Request.Post(endpointUrl)
            .setHeader("Content-Type", "text/turtle;charset=utf-8")
            .setHeader(authHeader)
            .bodyFile(file, ContentType.create("text/turtle", StandardCharsets.UTF_8));
    executeAndConsume(request);
  }


  @Override
  public Model queryForModel(String query) {
    //pass default graph, method without default graph doesn't use the HttpContext !!!
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(endpointUrl + "/query", query, null, null, authenticationHttpContext)) {
      //jena adds empty defaultGraph param to URL because defaultGraph is null but is a "value", stardog doesn't like that
      ((QueryEngineHTTP) queryExecution).setDefaultGraphURIs(Collections.emptyList());
      return queryExecution.execConstruct();
    }
  }

  @Override
  public void executeUpdateQuery(String updateQuery) {
    Request request = Request
            .Post(endpointUrl + "/update")
            .setHeader(authHeader)
            .body(new UrlEncodedFormEntity(Collections.singletonList(new BasicNameValuePair("update", updateQuery)), StandardCharsets.UTF_8));
    executeAndConsume(request);
  }

  @Override
  public boolean executeAskQuery(String askQuery) {
    //pass default graph, method without default graph doesn't use the HttpContext !!!
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(endpointUrl + "/query", askQuery, null, null, authenticationHttpContext)) {
      //jena adds empty defaultGraph param to URL because defaultGraph is null but is a "value", stardog doesn't like that
      ((QueryEngineHTTP) queryExecution).setDefaultGraphURIs(Collections.emptyList());
      return queryExecution.execAsk();
    }
  }

  @Override
  public void upload(Model model, String graphUri) {
    updateGraph(graphUri, model);
  }

  @Override
  public void updateGraph(String graphUri, Model model) {
    upload(model, graphUri, false);
  }

  @Override
  public void replaceGraph(String graphUri, Model model) {
    upload(model, graphUri, true);
  }

  private void upload(Model model, String graphUri, boolean replace) {
    String graphStoreUrl = endpointUrl + "?graph=" + graphUri;
    byte[] body = JenaUtils.toByteArray(model, TripleSerializationFormat.turtle);
    Request request = (replace ? Request.Put(graphStoreUrl) : Request.Post(graphStoreUrl))  //Put replaces the graph, Post adds data
            .setHeader("Content-Type", "text/turtle;charset=utf-8")
            .setHeader(authHeader)
            .bodyByteArray(body, ContentType.create("text/turtle", StandardCharsets.UTF_8));
    executeAndConsume(request);
  }

  @Override
  public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
    //pass default graph, method without default graph doesn't use the HttpContext !!!
    try (QueryExecution queryExecution = QueryExecutionFactory.sparqlService(endpointUrl + "/query", query, null, null, authenticationHttpContext)) {
      //jena adds empty defaultGraph param to URL because defaultGraph is null but is a "value", stardog doesn't like that
      ((QueryEngineHTTP) queryExecution).setDefaultGraphURIs(Collections.emptyList());
      return resultHandler.apply(queryExecution.execSelect());
    }
  }

  @Override
  public void dropGraph(String graphUri) {
    String graphStoreUrl = endpointUrl + "?graph=" + graphUri;
    Request request = Request.Delete(graphStoreUrl)
            .setHeader(authHeader);
    executeAndConsume(request);
  }
}
