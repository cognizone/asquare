package zone.cogni.sem.jena.template;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies JenaQueryUtils.newQueryExecution overloads work correctly after
 * migrating from QueryExecutionFactory.create(Query, Model, QuerySolution)
 * (removed in Jena 6) to QueryExecutionDatasetBuilder.
 */
public class JenaQueryUtilsTest {

  private static final String SUBJECT   = "http://demo.com/data/person/1";
  private static final String TYPE_URI  = "http://demo.com/onto/Person";
  private static final String NAME_PROP = "http://demo.com/onto/name";
  private static final String RDF_TYPE  = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  private Model model;

  @BeforeEach
  public void setUp() {
    model = ModelFactory.createDefaultModel();
    model.add(ResourceFactory.createResource(SUBJECT),
              ResourceFactory.createProperty(RDF_TYPE),
              ResourceFactory.createResource(TYPE_URI));
    model.add(ResourceFactory.createResource(SUBJECT),
              ResourceFactory.createProperty(NAME_PROP),
              ResourceFactory.createPlainLiteral("Fred"));
  }

  // --- newQueryExecution(Model, String) ---

  @Test
  public void stringOverload_selectReturnsResults() {
    String sparql = "SELECT ?name WHERE { <" + SUBJECT + "> <" + NAME_PROP + "> ?name }";
    try (QueryExecution qe = JenaQueryUtils.newQueryExecution(model, sparql)) {
      assertNotNull(qe);
      List<Map<String, org.apache.jena.rdf.model.RDFNode>> rows =
          JenaQueryUtils.convertToListOfMaps(qe.execSelect());
      assertEquals(1, rows.size());
      assertEquals("Fred", rows.getFirst().get("name").asLiteral().getString());
    }
  }

  @Test
  public void stringOverload_askExistingResourceReturnsTrue() {
    String sparql = "ASK { <" + SUBJECT + "> a <" + TYPE_URI + "> }";
    try (QueryExecution qe = JenaQueryUtils.newQueryExecution(model, sparql)) {
      assertTrue(qe.execAsk());
    }
  }

  // --- newQueryExecution(Model, Query) ---

  @Test
  public void queryOverload_selectReturnsResults() {
    Query query = QueryFactory.create("SELECT ?name WHERE { <" + SUBJECT + "> <" + NAME_PROP + "> ?name }");
    try (QueryExecution qe = JenaQueryUtils.newQueryExecution(model, query)) {
      assertNotNull(qe);
      List<Map<String, org.apache.jena.rdf.model.RDFNode>> rows =
          JenaQueryUtils.convertToListOfMaps(qe.execSelect());
      assertEquals(1, rows.size());
    }
  }

  // --- newQueryExecution(Model, String, QuerySolution) with binding ---

  @Test
  public void stringWithBinding_filtersCorrectly() {
    // Add a second person to confirm binding restricts results
    model.add(ResourceFactory.createResource("http://demo.com/data/person/2"),
              ResourceFactory.createProperty(NAME_PROP),
              ResourceFactory.createPlainLiteral("Annie"));

    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add("person", ResourceFactory.createResource(SUBJECT));

    String sparql = "SELECT ?name WHERE { ?person <" + NAME_PROP + "> ?name }";
    try (QueryExecution qe = JenaQueryUtils.newQueryExecution(model, sparql, bindings)) {
      List<Map<String, org.apache.jena.rdf.model.RDFNode>> rows =
          JenaQueryUtils.convertToListOfMaps(qe.execSelect());
      assertEquals(1, rows.size());
      assertEquals("Fred", rows.getFirst().get("name").asLiteral().getString());
    }
  }

  @Test
  public void stringWithNullBinding_returnsAllResults() {
    model.add(ResourceFactory.createResource("http://demo.com/data/person/2"),
              ResourceFactory.createProperty(NAME_PROP),
              ResourceFactory.createPlainLiteral("Annie"));

    String sparql = "SELECT ?name WHERE { ?s <" + NAME_PROP + "> ?name }";
    try (QueryExecution qe = JenaQueryUtils.newQueryExecution(model, sparql, null)) {
      List<Map<String, org.apache.jena.rdf.model.RDFNode>> rows =
          JenaQueryUtils.convertToListOfMaps(qe.execSelect());
      assertEquals(2, rows.size());
    }
  }

  // --- newQueryExecution(Model, Query, QuerySolution) with binding ---

  @Test
  public void queryWithBinding_filtersCorrectly() {
    model.add(ResourceFactory.createResource("http://demo.com/data/person/2"),
              ResourceFactory.createProperty(NAME_PROP),
              ResourceFactory.createPlainLiteral("Annie"));

    Query query = QueryFactory.create("SELECT ?name WHERE { ?person <" + NAME_PROP + "> ?name }");
    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add("person", ResourceFactory.createResource(SUBJECT));

    try (QueryExecution qe = JenaQueryUtils.newQueryExecution(model, query, bindings)) {
      List<Map<String, org.apache.jena.rdf.model.RDFNode>> rows =
          JenaQueryUtils.convertToListOfMaps(qe.execSelect());
      assertEquals(1, rows.size());
      assertEquals("Fred", rows.getFirst().get("name").asLiteral().getString());
    }
  }

  @Test
  public void queryWithNullBinding_returnsAllResults() {
    model.add(ResourceFactory.createResource("http://demo.com/data/person/2"),
              ResourceFactory.createProperty(NAME_PROP),
              ResourceFactory.createPlainLiteral("Annie"));

    Query query = QueryFactory.create("SELECT ?name WHERE { ?s <" + NAME_PROP + "> ?name }");
    try (QueryExecution qe = JenaQueryUtils.newQueryExecution(model, query, null)) {
      List<Map<String, org.apache.jena.rdf.model.RDFNode>> rows =
          JenaQueryUtils.convertToListOfMaps(qe.execSelect());
      assertEquals(2, rows.size());
    }
  }

  // --- closeQuietly ---

  @Test
  public void closeQuietly_onNull_doesNotThrow() {
    JenaQueryUtils.closeQuietly((QueryExecution) null);
    JenaQueryUtils.closeQuietly((Model) null);
  }

  @Test
  public void closeQuietly_onValidExecution_doesNotThrow() {
    QueryExecution qe = JenaQueryUtils.newQueryExecution(model,
        "SELECT ?s WHERE { ?s ?p ?o }");
    JenaQueryUtils.closeQuietly(qe);
  }

  // --- convertToListOfMaps ---

  @Test
  public void convertToListOfMaps_preservesNullVariables() {
    // ?missing is projected but not bound for person/1 (which has no ?extra triple)
    String sparql = "SELECT ?name ?missing WHERE { <" + SUBJECT + "> <" + NAME_PROP + "> ?name }";
    try (QueryExecution qe = JenaQueryUtils.newQueryExecution(model, sparql)) {
      List<Map<String, org.apache.jena.rdf.model.RDFNode>> rows =
          JenaQueryUtils.convertToListOfMaps(qe.execSelect());
      assertEquals(1, rows.size());
      assertTrue(rows.getFirst().containsKey("name"));
      assertTrue(rows.getFirst().containsKey("missing"));
      assertNotNull(rows.getFirst().get("name"));
    }
  }
}
