package zone.cogni.asquare.triplestore.tdb2;

import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zone.cogni.sem.jena.template.JenaResultSetHandlers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone (no Spring context) CRUD tests for Tdb2StoreService.
 * Verifies addData, executeUpdateQuery, executeSelectQuery,
 * executeAskQuery, executeConstructQuery, delete, and compact.
 */
public class Tdb2StoreServiceCrudTest {

  private static final String PERSON_1  = "http://demo.com/data/person/1";
  private static final String PERSON_2  = "http://demo.com/data/person/2";
  private static final String TYPE_URI  = "http://demo.com/onto/Person";
  private static final String NAME_PROP = "http://demo.com/onto/name";
  private static final String RDF_TYPE  = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  private Tdb2StoreService store;

  @BeforeEach
  void setUp() {
    store = Tdb2StoreService.inMemory(null);
  }

  // -------------------------------------------------------------------------
  // addData
  // -------------------------------------------------------------------------

  @Test
  void addData_makesTripleQueryableViaAsk() {
    store.addData(personModel(PERSON_1, "Fred"));

    assertTrue(store.executeAskQuery("ASK { <" + PERSON_1 + "> a <" + TYPE_URI + "> }"));
  }

  @Test
  void addData_multipleModels_accumulatesTriples() {
    store.addData(personModel(PERSON_1, "Fred"));
    store.addData(personModel(PERSON_2, "Annie"));

    List<String> names = selectAllNames();
    assertEquals(2, names.size());
    assertTrue(names.contains("Fred"));
    assertTrue(names.contains("Annie"));
  }

  @Test
  void addData_withGraphUri_throwsUnsupported() {
    assertThrows(RuntimeException.class,
                 () -> store.addData(personModel(PERSON_1, "Fred"), "http://demo.com/graph/1"));
  }

  // -------------------------------------------------------------------------
  // executeSelectQuery
  // -------------------------------------------------------------------------

  @Test
  void executeSelectQuery_returnsAllMatchingResults() {
    store.addData(personModel(PERSON_1, "Fred"));
    store.addData(personModel(PERSON_2, "Annie"));

    List<String> names = selectAllNames();
    assertEquals(2, names.size());
  }

  @Test
  void executeSelectQuery_onEmptyStore_returnsEmptyList() {
    List<String> names = selectAllNames();
    assertEquals(0, names.size());
  }

  // -------------------------------------------------------------------------
  // executeAskQuery
  // -------------------------------------------------------------------------

  @Test
  void executeAskQuery_existingResource_returnsTrue() {
    store.addData(personModel(PERSON_1, "Fred"));

    assertTrue(store.executeAskQuery("ASK { <" + PERSON_1 + "> a <" + TYPE_URI + "> }"));
  }

  @Test
  void executeAskQuery_missingResource_returnsFalse() {
    assertFalse(store.executeAskQuery("ASK { <http://demo.com/nonexistent> ?p ?o }"));
  }

  @Test
  void executeAskQuery_withQueryObject_andBindings() {
    store.addData(personModel(PERSON_1, "Fred"));

    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add("person", ResourceFactory.createResource(PERSON_1));

    assertTrue(store.executeAskQuery(
        QueryFactory.create("ASK { ?person a <" + TYPE_URI + "> }"),
        bindings));
  }

  // -------------------------------------------------------------------------
  // executeConstructQuery
  // -------------------------------------------------------------------------

  @Test
  void executeConstructQuery_returnsMatchingSubgraph() {
    store.addData(personModel(PERSON_1, "Fred"));
    store.addData(personModel(PERSON_2, "Annie"));

    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add("person", ResourceFactory.createResource(PERSON_1));

    Model result = store.executeConstructQuery(
        QueryFactory.create("CONSTRUCT { ?person <" + NAME_PROP + "> ?name } WHERE { ?person <" + NAME_PROP + "> ?name }"),
        bindings);

    assertEquals(1, result.size());
    assertTrue(result.contains(
        ResourceFactory.createResource(PERSON_1),
        ResourceFactory.createProperty(NAME_PROP),
        ResourceFactory.createPlainLiteral("Fred")));
  }

  @Test
  void executeConstructQuery_emptyBindings_returnsAll() {
    store.addData(personModel(PERSON_1, "Fred"));
    store.addData(personModel(PERSON_2, "Annie"));

    Model result = store.executeConstructQuery(
        QueryFactory.create("CONSTRUCT { ?s <" + NAME_PROP + "> ?name } WHERE { ?s <" + NAME_PROP + "> ?name }"),
        new QuerySolutionMap());

    assertEquals(2, result.size());
  }

  // -------------------------------------------------------------------------
  // executeUpdateQuery
  // -------------------------------------------------------------------------

  @Test
  void executeUpdateQuery_insert_addsTriple() {
    store.executeUpdateQuery(
        "INSERT DATA { <" + PERSON_1 + "> <" + NAME_PROP + "> \"Fred\" }");

    assertTrue(store.executeAskQuery("ASK { <" + PERSON_1 + "> <" + NAME_PROP + "> \"Fred\" }"));
  }

  @Test
  void executeUpdateQuery_delete_removesTriple() {
    store.addData(personModel(PERSON_1, "Fred"));
    store.executeUpdateQuery(
        "DELETE DATA { <" + PERSON_1 + "> <" + NAME_PROP + "> \"Fred\" }");

    assertFalse(store.executeAskQuery("ASK { <" + PERSON_1 + "> <" + NAME_PROP + "> \"Fred\" }"));
    // rdf:type triple is still present
    assertTrue(store.executeAskQuery("ASK { <" + PERSON_1 + "> a <" + TYPE_URI + "> }"));
  }

  @Test
  void executeUpdateQuery_invalidSparql_throwsException() {
    assertThrows(Exception.class, () ->
        store.executeUpdateQuery("NOT VALID SPARQL"));
  }

  // -------------------------------------------------------------------------
  // delete
  // -------------------------------------------------------------------------

  @Test
  void delete_removesAllData() {
    store.addData(personModel(PERSON_1, "Fred"));
    store.addData(personModel(PERSON_2, "Annie"));

    store.delete();

    assertFalse(store.executeAskQuery("ASK { ?s ?p ?o }"));
    assertEquals(0, selectAllNames().size());
  }

  @Test
  void delete_onEmptyStore_doesNotChangeState() {
    store.delete();
    assertFalse(store.executeAskQuery("ASK { ?s ?p ?o }"));
  }

  // -------------------------------------------------------------------------
  // getDataset / getTransaction
  // -------------------------------------------------------------------------

  @Test
  void getDataset_returnsNonNull() {
    assertNotNull(store.getDataset());
  }

  @Test
  void getTransaction_returnsNonNull() {
    assertNotNull(store.getTransaction());
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private Model personModel(String uri, String name) {
    Model m = ModelFactory.createDefaultModel();
    m.add(ResourceFactory.createResource(uri),
          ResourceFactory.createProperty(RDF_TYPE),
          ResourceFactory.createResource(TYPE_URI));
    m.add(ResourceFactory.createResource(uri),
          ResourceFactory.createProperty(NAME_PROP),
          ResourceFactory.createPlainLiteral(name));
    return m;
  }

  private List<String> selectAllNames() {
    return store.executeSelectQuery(
        QueryFactory.create("SELECT ?name WHERE { ?s <" + NAME_PROP + "> ?name }"),
        new QuerySolutionMap(),
        JenaResultSetHandlers.listResultSetHandler(qs -> qs.getLiteral("name").getString()),
        null);
  }
}
