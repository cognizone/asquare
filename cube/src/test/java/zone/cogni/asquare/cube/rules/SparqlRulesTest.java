package zone.cogni.asquare.cube.rules;

import com.google.common.collect.ImmutableMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.sem.jena.JenaUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {SparqlRulesTestConfig.class})
class SparqlRulesTest {

  @Autowired
  SparqlRules personRules;

  @Test
  public void test_rules() {
    Model model = JenaUtils.read(new ClassPathResource("rules/model.ttl"));
    long oldModelSize = model.size();

    Model result = personRules.convert(model, "person-edit", ImmutableMap.of("personName", "Homer Simpson"));
    assertThat(result.size()).isEqualTo(oldModelSize);

    Resource homer = result.getResource("http://demo.com/data#homer");
    Property hasDate = result.getProperty("http://demo.com/person/model#hasDate");
    Property hasId = model.getProperty("http://demo.com/person/model#hasId");
    assertTrue(result.contains(homer, hasDate));
    assertFalse(result.contains(homer, hasId));

  }

}