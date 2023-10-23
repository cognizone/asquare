package zone.cogni.asquare.cube.sort;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.sem.jena.JenaUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SkolemizedModelTest {

  @Test
  public void blank_nodes_as_skolem_uris() {
    // given
    Model model = loadModel("sort/blank-node-with-nested-blank-node-with-two-triples.ttl");

    // when
    Model skolemizedModel = new SkolemizedModel("http://cogni.zone").apply(model);

    // then
    assertThat(skolemizedModel.size()).isEqualTo(3);

    Resource skolemUri1 = ResourceFactory.createResource("http://cogni.zone/.well-known/genid/548056f0698a0e410152cdeec9d9a8c9242dfe2b13f813658e40bfec1979856f");
    assertThat(skolemizedModel.listStatements(skolemUri1, null, (RDFNode) null).toList().size()).isEqualTo(1);

    Resource skolemUri2 = ResourceFactory.createResource("http://cogni.zone/.well-known/genid/a66dae80794f76cb551de9078eec8a5c5c12bcdc10023b9170016461e99e184a");
    assertThat(skolemizedModel.listStatements(skolemUri2, null, (RDFNode) null).toList().size()).isEqualTo(2);
  }

  private Model loadModel(String path) {
    return JenaUtils.read(new ClassPathResource(path));
  }

}