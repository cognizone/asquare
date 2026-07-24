package zone.cogni.sem.jena.template;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JenaQueryTemplateTest {

  private static final String PERSON_1    = "http://demo.com/data/person/1";
  private static final String PERSON_2    = "http://demo.com/data/person/2";
  private static final String PERSON_TYPE = "http://demo.com/onto/Person";
  private static final String NAME_PROP   = "http://demo.com/onto/name";
  private static final String RDF_TYPE    = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

  private Model model;

  @BeforeEach
  public void setUp() {
    model = ModelFactory.createDefaultModel();
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
  }

  @Test
  public void selectWithBindingsReturnsFilteredResult() {
    Query query = QueryFactory.create("SELECT ?name WHERE { ?person <" + NAME_PROP + "> ?name }");
    QuerySolution bindings = bindingFor("person", ResourceFactory.createResource(PERSON_1));

    List<Map<String, RDFNode>> results = JenaQueryTemplate.select(model, query, bindings, false);

    assertEquals(1, results.size());
    assertEquals("Fred", results.get(0).get("name").asLiteral().getString());
  }

  @Test
  public void selectWithoutBindingsReturnsAll() {
    Query query = QueryFactory.create("SELECT ?name WHERE { ?person <" + NAME_PROP + "> ?name }");

    List<Map<String, RDFNode>> results = JenaQueryTemplate.select(model, query, (QuerySolution) null, false);

    assertEquals(2, results.size());
  }

  @Test
  public void askWithBindingsTrue() {
    Query query = QueryFactory.create("ASK { ?person a <" + PERSON_TYPE + "> }");
    QuerySolution bindings = bindingFor("person", ResourceFactory.createResource(PERSON_1));

    assertTrue(JenaQueryTemplate.ask(model, query, bindings));
  }

  @Test
  public void askWithBindingsFalse() {
    Query query = QueryFactory.create("ASK { ?person a <" + PERSON_TYPE + "> }");
    QuerySolution bindings = bindingFor("person", ResourceFactory.createResource("http://demo.com/data/person/99"));

    assertFalse(JenaQueryTemplate.ask(model, query, bindings));
  }

  @Test
  public void constructWithBindingsReturnsFilteredModel() {
    Query query = QueryFactory.create(
      "CONSTRUCT { ?person <" + NAME_PROP + "> ?name } WHERE { ?person <" + NAME_PROP + "> ?name }");
    QuerySolution bindings = bindingFor("person", ResourceFactory.createResource(PERSON_1));

    Model result = JenaQueryTemplate.construct(model, query, bindings);

    assertEquals(1, result.size());
    assertTrue(result.contains(ResourceFactory.createResource(PERSON_1),
                               ResourceFactory.createProperty(NAME_PROP),
                               ResourceFactory.createPlainLiteral("Fred")));
  }

  private static QuerySolutionMap bindingFor(String var, RDFNode value) {
    QuerySolutionMap map = new QuerySolutionMap();
    map.add(var, value);
    return map;
  }
}
