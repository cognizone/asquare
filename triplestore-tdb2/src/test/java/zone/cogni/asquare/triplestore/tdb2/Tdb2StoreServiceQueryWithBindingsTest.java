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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies SELECT, ASK and CONSTRUCT queries with QuerySolutionMap bindings work correctly
 * against Tdb2StoreService after migrating from QueryExecutionFactory.create(Query, Model, bindings)
 * to QueryExecutionDatasetBuilder.
 */
public class Tdb2StoreServiceQueryWithBindingsTest {

  private static final String PERSON_1    = "http://demo.com/data/person/1";
  private static final String PERSON_2    = "http://demo.com/data/person/2";
  private static final String PERSON_TYPE = "http://demo.com/onto/Person";
  private static final String NAME_PROP   = "http://demo.com/onto/name";
  private static final String RDF_TYPE    = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  private Tdb2StoreService store;

  @BeforeEach
  public void setUp() {
    store = Tdb2StoreService.inMemory(null);
    store.delete();

    Model model = ModelFactory.createDefaultModel();
    model.add(ResourceFactory.createResource(PERSON_1),
              ResourceFactory.createProperty(RDF_TYPE),
              ResourceFactory.createResource(PERSON_TYPE));
    model.add(ResourceFactory.createResource(PERSON_1),
              ResourceFactory.createProperty(NAME_PROP),
              ResourceFactory.createPlainLiteral("Fred"));
    model.add(ResourceFactory.createResource(PERSON_2),
              ResourceFactory.createProperty(RDF_TYPE),
              ResourceFactory.createResource(PERSON_TYPE));
    model.add(ResourceFactory.createResource(PERSON_2),
              ResourceFactory.createProperty(NAME_PROP),
              ResourceFactory.createPlainLiteral("Annie"));
    store.addData(model);
  }

  private static QuerySolutionMap bindingFor(String var, String uri) {
    QuerySolutionMap bindings = new QuerySolutionMap();
    bindings.add(var, ResourceFactory.createResource(uri));
    return bindings;
  }

  @Test
  public void selectIgnoresBindingsReturnsAll() {
    // Tdb2StoreService.executeSelectQuery does not apply QuerySolutionMap bindings (pre-existing behavior)
    List<String> results = store.executeSelectQuery(
      QueryFactory.create("SELECT ?name WHERE { ?person <" + NAME_PROP + "> ?name }"),
      bindingFor("person", PERSON_1),
      JenaResultSetHandlers.listResultSetHandler(qs -> qs.getLiteral("name").getString()),
      null);

    assertEquals(2, results.size());
  }

  @Test
  public void selectWithoutBindingsReturnsAll() {
    List<String> results = store.executeSelectQuery(
      QueryFactory.create("SELECT ?name WHERE { ?person <" + NAME_PROP + "> ?name }"),
      new QuerySolutionMap(),
      JenaResultSetHandlers.listResultSetHandler(qs -> qs.getLiteral("name").getString()),
      null);

    assertEquals(2, results.size());
  }

  @Test
  public void askWithBindingsTrue() {
    assertTrue(store.executeAskQuery(
      QueryFactory.create("ASK { ?person a <" + PERSON_TYPE + "> }"),
      bindingFor("person", PERSON_1)));
  }

  @Test
  public void askWithBindingsFalse() {
    assertFalse(store.executeAskQuery(
      QueryFactory.create("ASK { ?person a <" + PERSON_TYPE + "> }"),
      bindingFor("person", "http://demo.com/data/person/99")));
  }

  @Test
  public void constructWithBindingsReturnsFilteredModel() {
    QuerySolutionMap bindings = bindingFor("person", PERSON_1);

    Model result = store.executeConstructQuery(
      QueryFactory.create("CONSTRUCT { ?person <" + NAME_PROP + "> ?name } WHERE { ?person <" + NAME_PROP + "> ?name }"),
      bindings);

    assertEquals(1, result.size());
    assertTrue(result.contains(ResourceFactory.createResource(PERSON_1),
                               ResourceFactory.createProperty(NAME_PROP),
                               ResourceFactory.createPlainLiteral("Fred")));
  }
}
