package zone.cogni.asquare.cube.sort;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.libs.jena.utils.JenaUtils;

import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class DeskolemizedModelTest {
  @Test
  public void blank_nodes_as_skolem_uris() {
    // given
    String prefix = "http://cogni.zone";
    Function<Model, Model> convertModel = new SkolemizedModel(prefix).andThen(new DeskolemizedModel(prefix));
    Model model = loadModel("sort/blank-node-with-nested-blank-node-with-two-triples.ttl");

    // when
    Model deskolemizedModel = convertModel.apply(model);

    // then
    assertThat(deskolemizedModel.size()).isEqualTo(3);

    Set<Resource> subjects = deskolemizedModel.listSubjects().toSet();
    assertThat(subjects).size().isEqualTo(2);
    assertThat(subjects).allMatch(r -> r.isAnon());
  }

  private Model loadModel(String path) {
    return JenaUtils.read(new ClassPathResource(path));
  }

}
