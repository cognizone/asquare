package zone.cogni.asquare.triplestore.jenamemory;

import org.apache.jena.query.Dataset;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies SELECT, ASK and CONSTRUCT queries with QuerySolutionMap bindings work correctly
 * after migrating from QueryExecutionFactory.create(Query, Model, bindings)
 * to QueryExecutionDatasetBuilder.
 */
public class RdfStoreServiceQueryWithBindingsTest {

  private static final String PERSON_1    = "http://demo.com/data/person/1";
  private static final String PERSON_2    = "http://demo.com/data/person/2";
  private static final String PERSON_TYPE = "http://demo.com/onto/Person";
  private static final String NAME_PROP   = "http://demo.com/onto/name";
  private static final String RDF_TYPE    = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  private Model testModel;

  @BeforeEach
  public void setUp() {
    testModel = ModelFactory.createDefaultModel();
    testModel.add(ResourceFactory.createResource(PERSON_1),
                  ResourceFactory.createProperty(RDF_TYPE),
                  ResourceFactory.createResource(PERSON_TYPE));
    testModel.add(ResourceFactory.createResource(PERSON_1),
                  ResourceFactory.createProperty(NAME_PROP),
                  ResourceFactory.createPlainLiteral("Fred"));
    testModel.add(ResourceFactory.createResource(PERSON_2),
                  ResourceFactory.createProperty(RDF_TYPE),
                  ResourceFactory.createResource(PERSON_TYPE));
    testModel.add(ResourceFactory.createResource(PERSON_2),
                  ResourceFactory.createProperty(NAME_PROP),
                  ResourceFactory.createPlainLiteral("Annie"));
  }

  private static QuerySolutionMap bindingFor(String var, String uri) {
    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add(var, ResourceFactory.createResource(uri));
    return bindings;
  }

  private void assertSelectWithBindings(RdfStoreService store) {
    QuerySolutionMap bindings = bindingFor("person", PERSON_1);

    List<String> results = store.executeSelectQuery(
      QueryFactory.create("SELECT ?name WHERE { ?person <" + NAME_PROP + "> ?name }"),
      bindings,
      JenaResultSetHandlers.listResultSetHandler(qs -> qs.getLiteral("name").getString()),
      null);

    assertEquals(1, results.size());
    assertEquals("Fred", results.get(0));
  }

  private void assertAskWithBindings(RdfStoreService store) {
    assertTrue(store.executeAskQuery(
      QueryFactory.create("ASK { ?person a <" + PERSON_TYPE + "> }"),
      bindingFor("person", PERSON_1)));
    assertFalse(store.executeAskQuery(
      QueryFactory.create("ASK { ?person a <" + PERSON_TYPE + "> }"),
      bindingFor("person", "http://demo.com/data/person/99")));
  }

  private void assertConstructWithBindings(RdfStoreService store) {
    QuerySolutionMap bindings = bindingFor("person", PERSON_1);

    Model result = store.executeConstructQuery(
      QueryFactory.create("CONSTRUCT { ?person <" + NAME_PROP + "> ?name } WHERE { ?person <" + NAME_PROP + "> ?name }"),
      bindings);

    assertEquals(1, result.size());
    assertTrue(result.contains(ResourceFactory.createResource(PERSON_1),
                               ResourceFactory.createProperty(NAME_PROP),
                               ResourceFactory.createPlainLiteral("Fred")));
  }

  @Nested
  class InternalRdfStoreServiceTest {

    private RdfStoreService store;

    @BeforeEach
    public void setUp() {
      store = new InternalRdfStoreService(testModel);
    }

    @Test
    public void selectWithBindings() {
      assertSelectWithBindings(store);
    }

    @Test
    public void askWithBindings() {
      assertAskWithBindings(store);
    }

    @Test
    public void constructWithBindings() {
      assertConstructWithBindings(store);
    }
  }

  @Nested
  class DatasetRdfStoreServiceTest {

    private RdfStoreService store;

    @BeforeEach
    public void setUp() {
      Dataset dataset = DatasetFactory.create(testModel);
      store = new DatasetRdfStoreService(dataset);
    }

    @Test
    public void selectWithBindings() {
      assertSelectWithBindings(store);
    }

    @Test
    public void askWithBindings() {
      assertAskWithBindings(store);
    }

    @Test
    public void constructWithBindings() {
      assertConstructWithBindings(store);
    }
  }

  @Nested
  class InMemoryDatabaseTest {

    private RdfStoreService store;

    @BeforeEach
    public void setUp() {
      JenaModel jenaModel = new JenaModel() {
        @Override
        public Model get() {
          return testModel;
        }
      };
      InMemoryDatabase db = new InMemoryDatabase();
      db.setJenaModel(jenaModel);
      store = db;
    }

    @Test
    public void selectWithBindings() {
      assertSelectWithBindings(store);
    }

    @Test
    public void askWithBindings() {
      assertAskWithBindings(store);
    }

    @Test
    public void constructWithBindings() {
      assertConstructWithBindings(store);
    }
  }
}
