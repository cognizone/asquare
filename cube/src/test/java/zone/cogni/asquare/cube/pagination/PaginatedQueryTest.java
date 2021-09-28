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
  PaginatedQuery paginatedQuery;

  @Test
  public void test_construction() {
    // given
    int batchSize = 100;

    // when
    PaginatedQuery constructorCall = new PaginatedQuery(batchSize);

    // then it compiles!
  }

  @Test
  public void test_model() {
    Model model = JenaUtils.read(new ClassPathResource("pagination/homer.ttl"));
    String constructQuery = "construct { ?s ?p ?o } where { ?s ?p ?o }";

    RdfStoreService rdfStore = paginatedQuery.getRdfStore(model);

    Model smart = paginatedQuery.getModel(rdfStore, constructQuery);
    assertEquals(model.size(), smart.size());

  }

}