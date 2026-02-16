package zone.cogni.libs.sparqlservice.impl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.libs.sparqlservice.SparqlService;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;


public class HanamiGraphDBSparqlService implements SparqlService {

    private static final Logger log = LoggerFactory.getLogger(GraphDBSparqlService.class);
    private final GraphDBConfig config;
    private HttpClient httpClient;

    private synchronized HttpClient getHttpClient() {
        if (httpClient != null) return httpClient;

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault());

        if (StringUtils.isNoneBlank(config.getUser(), config.getPassword())) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            config.getUser(),
                            config.getPassword().toCharArray());
                }
            });
        } else if (!StringUtils.isAllBlank(config.getUser(), config.getPassword())) {
            log.error("Endpoint credentials not properly configured");
        }

        httpClient = builder.build();
        return httpClient;
    }

    public HanamiGraphDBSparqlService(GraphDBConfig config) {
        this.config = config;
    }

    @Override
    public void uploadTtlFile(File file) {
        try {
            uploadTtl(file.toURI().toString(), FileUtils.readFileToString(file, "UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read file " + file.getName(), e);
        }
    }

    @Override
    public void upload(Model model, String graphUri) {
        StringWriter writer = new StringWriter();
        model.write(writer, "ttl");
        uploadTtl(graphUri, writer.toString());
    }

    @Override
    public Model queryForModel(String query) {
        try (QueryExecution queryExecution = QueryExecutionHTTP.service(config.getSparqlEndpoint())
                .queryString(query)
                .httpClient(getHttpClient())
                .build()) {
            return queryExecution.execConstruct();
        }
    }

    @Override
    public void executeUpdateQuery(String updateQuery) {
        String formBody = "update=" + URLEncoder.encode(updateQuery, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getSparqlUpdateEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        executeHttpRequest(request, 204);
    }

    @Override
    public <R> R executeSelectQuery(String query, Function<ResultSet, R> resultHandler) {
        try (QueryExecution queryExecution = QueryExecutionHTTP.service(config.getSparqlEndpoint())
                .queryString(query)
                .httpClient(getHttpClient())
                .build()) {
            return resultHandler.apply(queryExecution.execSelect());
        }
    }

    @Override
    public boolean executeAskQuery(String askQuery) {
        try (QueryExecution queryExecution = QueryExecutionHTTP.service(config.getSparqlEndpoint())
                .queryString(askQuery)
                .httpClient(getHttpClient())
                .build()) {
            return queryExecution.execAsk();
        }
    }


    @Override
    public void dropGraph(String graphUri) {
        executeUpdateQuery("clear graph <" + graphUri + ">");
    }

    private void executeHttpRequest(HttpRequest request, int expectedCode) {
        try {
            HttpResponse<String> response =
                    getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            checkResponse(response, expectedCode);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void checkResponse(HttpResponse<String> response, int expectedCode) {
        int actual = response.statusCode();
        if (actual == expectedCode) return;

        String body = response.body();
        if (body == null || body.isBlank()) {
            body = "No response body from server.";
        }

        String msg = "Update didn't answer " + expectedCode +
                " code: HTTP " + actual + ". " + body;

        throw new RuntimeException(msg);
    }



    public void uploadTtl(String graphUri, String turtleContent) {
        log.info("Uploading Turtle data to GraphDB repository for graph: {}", graphUri);

        String encoded = URLEncoder.encode("<" + graphUri + ">", StandardCharsets.UTF_8);
        URI endpoint = URI.create(config.getSparqlUpdateEndpoint() + "?context=" + encoded);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", Lang.TURTLE.getHeaderString())
                .POST(HttpRequest.BodyPublishers.ofString(turtleContent, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<Void> resp = getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());

            int code = resp.statusCode();
            if (code != 200 && code != 204 && code != 202) {
                log.error("Failed to upload Turtle data. HTTP {} {}", code, resp);
                throw new RuntimeException("Failed to upload TTL data. HTTP " + code);
            }
            log.debug("Successfully uploaded Turtle data to graph {} (HTTP {})", graphUri, code);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error uploading Turtle data to GraphDB", e);
            throw new RuntimeException("I/O error during Turtle upload", e);
        }
    }

    @Override
    public void replaceGraph(String graphUri, Model model) {
        StringWriter writer = new StringWriter();
        model.write(writer, "ttl");
        String turtleContent = writer.toString();

        log.info("Replacing graph {} with single PUT request", graphUri);

        String encoded = URLEncoder.encode("<" + graphUri + ">", StandardCharsets.UTF_8);
        URI endpoint = URI.create(config.getSparqlUpdateEndpoint() + "?context=" + encoded);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", Lang.TURTLE.getHeaderString())
                .PUT(HttpRequest.BodyPublishers.ofString(turtleContent, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<Void> resp = getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());

            int code = resp.statusCode();
            if (code != 200 && code != 204 && code != 202) {
                log.error("Failed to upload Turtle data. HTTP {} {}", code, resp);
                throw new RuntimeException("Failed to upload TTL data. HTTP " + code);
            }
            log.debug("Graph {} successfully replaced (HTTP {})", graphUri, code);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();               // preserve interrupt flag
            log.error("Error replacing Turtle data to GraphDB", e);
            throw new RuntimeException("I/O error during graph replacement", e);
        }
    }

}