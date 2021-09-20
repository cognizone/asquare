package zone.cogni.asquare.cube.pagination;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.sem.jena.JenaUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {PaginatedQueryTestConfig.class})
class PaginatedQueryTest {

  @Autowired
  PaginatedQuery smartPaginatedQuery;
  @Autowired
  PaginatedQuery simplePaginatedQuery;

  @Test
  public void test_smart() {
    Model model = JenaUtils.read(new ClassPathResource("pagination/homer.ttl"));
    String constructQuery = "construct { ?s ?p ?o } where { ?s ?p ?o }";

    RdfStoreService rdfStore = smartPaginatedQuery.getRdfStore(model);

    Model smart = smartPaginatedQuery.getModel(rdfStore, constructQuery);
    assertEquals(model.size(), smart.size());

  }

  @Test
  public void test_simple() {
    Model model = JenaUtils.read(new ClassPathResource("pagination/homer.ttl"));
    String constructQuery = "construct { ?s ?p ?o } where { ?s ?p ?o }";

    RdfStoreService rdfStore = simplePaginatedQuery.getRdfStore(model);

    Model simple = simplePaginatedQuery.getModel(rdfStore, constructQuery);
    assertEquals(model.size(), simple.size());

  }

}