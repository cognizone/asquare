package zone.cogni.asquare.edit.delta;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;

import static org.assertj.core.api.Assertions.assertThat;


public class DeltaTest {

  private static final Logger log = LoggerFactory.getLogger(DeltaTest.class);

  /**
   * Even 200.000 triple insert now works!?
   */
  @Test
  public void huge_sparql_update_in_one_insert() {
    // given
    InternalRdfStoreService rdfStore = new InternalRdfStoreService();
    Model addModel = getModel(200_000);

    Delta.PARTITION_SIZE = 1_000_000;
    Delta delta = new DeltaFake(addModel.listStatements().toList(), null);
    String sparql = delta.getSparql();
    log.info("(huge_sparql_update_in_one_insert) sparql size: {}", sparql.length());

    // when
    rdfStore.executeUpdateQuery(sparql);
    assertThat(rdfStore.getModel().size()).isEqualTo(200_000);
  }

  @Test
  public void huge_sparql_update_in_many_inserts() {
    // given
    InternalRdfStoreService rdfStore = new InternalRdfStoreService();
    Model addModel = getModel(20_000);

    Delta.PARTITION_SIZE = 1000;
    Delta delta = new DeltaFake(addModel.listStatements().toList(), null);
    String sparql = delta.getSparql();
    log.info("(huge_sparql_update_in_many_inserts) sparql size: {}", sparql.length());

    // when
    rdfStore.executeUpdateQuery(sparql);

    // then
    assertThat(rdfStore.getModel().size()).isEqualTo(20000);
  }

  private Model getModel(int size) {
    Model result = ModelFactory.createDefaultModel();
    for (int i = 1; i <= size; i++) {
      result.add(ResourceFactory.createStatement(
              ResourceFactory.createResource("http://demo.com/uri/" + i),
              ResourceFactory.createProperty("http://demo.com/property"),
              ResourceFactory.createStringLiteral("label " + i)
      ));
    }
    return result;
  }
}
