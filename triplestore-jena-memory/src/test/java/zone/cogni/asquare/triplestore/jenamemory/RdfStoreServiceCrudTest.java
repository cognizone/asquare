package zone.cogni.asquare.triplestore.jenamemory;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.template.JenaResultSetHandlers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the full CRUD lifecycle (addData, executeUpdateQuery, executeSelectQuery,
 * executeAskQuery, executeConstructQuery, delete) across all three in-memory
 * RdfStoreService implementations.
 */
public class RdfStoreServiceCrudTest {

  private static final String PERSON_1   = "http://demo.com/data/person/1";
  private static final String PERSON_2   = "http://demo.com/data/person/2";
  private static final String TYPE_URI   = "http://demo.com/onto/Person";
  private static final String NAME_PROP  = "http://demo.com/onto/name";
  private static final String RDF_TYPE   = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  /** Builds a single-person model for PERSON_1 (Fred). */
  private static Model fredModel() {
    Model m = ModelFactory.createDefaultModel();
    m.add(ResourceFactory.createResource(PERSON_1),
          ResourceFactory.createProperty(RDF_TYPE),
          ResourceFactory.createResource(TYPE_URI));
    m.add(ResourceFactory.createResource(PERSON_1),
          ResourceFactory.createProperty(NAME_PROP),
          ResourceFactory.createPlainLiteral("Fred"));
    return m;
  }

  private static List<String> selectNames(RdfStoreService store) {
    return store.executeSelectQuery(
        QueryFactory.create("SELECT ?name WHERE { ?s <" + NAME_PROP + "> ?name }"),
        new QuerySolutionMap(),
        JenaResultSetHandlers.listResultSetHandler(qs -> qs.getLiteral("name").getString()),
        null);
  }

  // -------------------------------------------------------------------------
  // InternalRdfStoreService
  // -------------------------------------------------------------------------

  @Nested
  class InternalRdfStoreServiceCrudTest {

    private RdfStoreService store;

    @BeforeEach
    void setUp() {
      store = new InternalRdfStoreService();
    }

    @Test
    void addData_makesDataQueryable() {
      store.addData(fredModel());

      assertTrue(store.executeAskQuery("ASK { <" + PERSON_1 + "> a <" + TYPE_URI + "> }"));
      assertEquals(List.of("Fred"), selectNames(store));
    }

    @Test
    void executeUpdateQuery_insertsNewTriple() {
      store.addData(fredModel());
      store.executeUpdateQuery(
          "INSERT DATA { <" + PERSON_2 + "> <" + NAME_PROP + "> \"Annie\" }");

      List<String> names = selectNames(store);
      assertEquals(2, names.size());
      assertTrue(names.contains("Annie"));
    }

    @Test
    void executeUpdateQuery_deletesTriple() {
      store.addData(fredModel());
      store.executeUpdateQuery(
          "DELETE DATA { <" + PERSON_1 + "> <" + NAME_PROP + "> \"Fred\" }");

      assertFalse(store.executeAskQuery("ASK { ?s <" + NAME_PROP + "> \"Fred\" }"));
      assertEquals(List.of(), selectNames(store));
    }

    @Test
    void executeConstructQuery_returnsMatchingSubgraph() {
      store.addData(fredModel());

      Model result = store.executeConstructQuery(
          QueryFactory.create("CONSTRUCT { ?s a <" + TYPE_URI + "> } WHERE { ?s a <" + TYPE_URI + "> }"),
          new QuerySolutionMap());

      assertEquals(1, result.size());
      assertTrue(result.contains(
          ResourceFactory.createResource(PERSON_1),
          ResourceFactory.createProperty(RDF_TYPE),
          ResourceFactory.createResource(TYPE_URI)));
    }

    @Test
    void delete_removesAllData() {
      store.addData(fredModel());
      store.delete();

      assertFalse(store.executeAskQuery("ASK { ?s ?p ?o }"));
      assertEquals(List.of(), selectNames(store));
    }

    @Test
    void addData_withGraphUri_throwsUnsupported() {
      assertThrows(RuntimeException.class,
                   () -> store.addData(fredModel(), "http://demo.com/graph/1"));
    }
  }

  // -------------------------------------------------------------------------
  // InMemoryDatabase
  // -------------------------------------------------------------------------

  @Nested
  class InMemoryDatabaseCrudTest {

    private RdfStoreService store;
    private Model sharedModel;

    @BeforeEach
    void setUp() {
      sharedModel = ModelFactory.createDefaultModel();
      JenaModel jenaModel = new JenaModel() {
        @Override
        public Model get() {
          return sharedModel;
        }
      };
      InMemoryDatabase db = new InMemoryDatabase();
      db.setJenaModel(jenaModel);
      store = db;
    }

    @Test
    void addData_makesDataQueryable() {
      store.addData(fredModel());

      assertTrue(store.executeAskQuery("ASK { <" + PERSON_1 + "> a <" + TYPE_URI + "> }"));
      assertEquals(List.of("Fred"), selectNames(store));
    }

    @Test
    void executeUpdateQuery_insertsNewTriple() {
      store.addData(fredModel());
      store.executeUpdateQuery(
          "INSERT DATA { <" + PERSON_2 + "> <" + NAME_PROP + "> \"Annie\" }");

      List<String> names = selectNames(store);
      assertEquals(2, names.size());
      assertTrue(names.contains("Annie"));
    }

    @Test
    void executeUpdateQuery_deletesTriple() {
      store.addData(fredModel());
      store.executeUpdateQuery(
          "DELETE DATA { <" + PERSON_1 + "> <" + NAME_PROP + "> \"Fred\" }");

      assertFalse(store.executeAskQuery("ASK { ?s <" + NAME_PROP + "> \"Fred\" }"));
    }

    @Test
    void executeConstructQuery_returnsMatchingSubgraph() {
      store.addData(fredModel());

      Model result = store.executeConstructQuery(
          QueryFactory.create("CONSTRUCT { ?s a <" + TYPE_URI + "> } WHERE { ?s a <" + TYPE_URI + "> }"),
          new QuerySolutionMap());

      assertEquals(1, result.size());
    }

    @Test
    void delete_removesAllData() {
      store.addData(fredModel());
      store.delete();

      assertFalse(store.executeAskQuery("ASK { ?s ?p ?o }"));
    }
  }

  // -------------------------------------------------------------------------
  // DatasetRdfStoreService
  // -------------------------------------------------------------------------

  @Nested
  class DatasetRdfStoreServiceCrudTest {

    private DatasetRdfStoreService store;

    @BeforeEach
    void setUp() {
      store = new DatasetRdfStoreService(DatasetFactory.createGeneral());
    }

    @Test
    void addData_makesDataQueryable() {
      store.addData(fredModel());

      assertTrue(store.executeAskQuery("ASK { <" + PERSON_1 + "> a <" + TYPE_URI + "> }"));
      assertEquals(List.of("Fred"), selectNames(store));
    }

    @Test
    void executeUpdateQuery_insertsNewTriple() {
      store.addData(fredModel());
      store.executeUpdateQuery(
          "INSERT DATA { <" + PERSON_2 + "> <" + NAME_PROP + "> \"Annie\" }");

      List<String> names = selectNames(store);
      assertEquals(2, names.size());
      assertTrue(names.contains("Annie"));
    }

    @Test
    void executeUpdateQuery_deletesTriple() {
      store.addData(fredModel());
      store.executeUpdateQuery(
          "DELETE DATA { <" + PERSON_1 + "> <" + NAME_PROP + "> \"Fred\" }");

      assertFalse(store.executeAskQuery("ASK { ?s <" + NAME_PROP + "> \"Fred\" }"));
    }

    @Test
    void executeConstructQuery_returnsMatchingSubgraph() {
      store.addData(fredModel());

      Model result = store.executeConstructQuery(
          QueryFactory.create("CONSTRUCT { ?s a <" + TYPE_URI + "> } WHERE { ?s a <" + TYPE_URI + "> }"),
          new QuerySolutionMap());

      assertEquals(1, result.size());
    }

    @Test
    void addData_withNamedGraph_storesInNamedGraph() {
      String graphUri = "http://demo.com/graph/1";
      store.addData(fredModel(), graphUri);

      assertTrue(store.executeAskQuery(
          "ASK { GRAPH <" + graphUri + "> { <" + PERSON_1 + "> a <" + TYPE_URI + "> } }"));
    }

    @Test
    void replaceGraph_replacesNamedGraphContent() {
      String graphUri = "http://demo.com/graph/1";
      store.addData(fredModel(), graphUri);

      Model replacement = ModelFactory.createDefaultModel();
      replacement.add(ResourceFactory.createResource(PERSON_2),
                      ResourceFactory.createProperty(NAME_PROP),
                      ResourceFactory.createPlainLiteral("Annie"));
      store.replaceGraph(graphUri, replacement);

      assertFalse(store.executeAskQuery(
          "ASK { GRAPH <" + graphUri + "> { <" + PERSON_1 + "> a <" + TYPE_URI + "> } }"));
      assertTrue(store.executeAskQuery(
          "ASK { GRAPH <" + graphUri + "> { <" + PERSON_2 + "> <" + NAME_PROP + "> \"Annie\" } }"));
    }

    @Test
    void deleteGraph_removesNamedGraph() {
      String graphUri = "http://demo.com/graph/1";
      store.addData(fredModel(), graphUri);
      store.deleteGraph(graphUri);

      assertFalse(store.executeAskQuery(
          "ASK { GRAPH <" + graphUri + "> { ?s ?p ?o } }"));
    }
  }
}
