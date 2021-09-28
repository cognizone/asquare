package zone.cogni.asquare.access.simplerdf.snippet;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.access.simplerdf.SimpleRdfAccessService;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.Snippet;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InMemoryDatabase;
import zone.cogni.asquare.triplestore.jenamemory.JenaModel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@EnableAutoConfiguration
@EnableConfigurationProperties
@Import({PrefixCcService.class})
@TestPropertySource(locations = "classpath:zone/cogni/asquare/access/simplerdf/snippet/snippet-query-test.properties")
class SnippetQueryTest {
  private final SnippetRegistry snippetRegistry = new SnippetRegistry();

  @Autowired
  private PrefixCcService prefixCcService;

  private RdfStoreService rdfStoreService;
  private SnippetQueryService snippetQueryService;

  Resource janUri = ResourceFactory.createResource("http://zone.cogni/data/jan");
  Literal janName = ResourceFactory.createPlainLiteral("Jan");

  @BeforeEach
  void before_each() {
    rdfStoreService = getRdfStoreService(new ClassPathResource("zone/cogni/asquare/access/simplerdf/snippet/snippet-query-data.ttl"));
    snippetQueryService = new SnippetQueryService(new SimpleRdfAccessService(prefixCcService, () -> rdfStoreService));
  }

  @Test
  void run_person_name() {
    Snippet snippet = snippetRegistry.getSnippet("person.name");

    List<Map<String, RDFNode>> result = snippetQueryService.run(snippet, janUri);

    Map<String, RDFNode> oneResult = assertOneResultWithType(result);
    assertThat(oneResult.values()).contains(janName);
  }

  @Test
  void run_person_findByName() {
    Snippet snippet = snippetRegistry.getSnippet("person.findByName");

    List<Map<String, RDFNode>> result = snippetQueryService.run(snippet, janName);
    Map<String, RDFNode> oneResult = assertOneResultWithType(result);

    assertThat(oneResult.values()).contains(janUri);
  }

  @Test
  void run_person_spouseName() {
    Snippet snippet = snippetRegistry.getSnippet("person.spouseName");

    List<Map<String, RDFNode>> result = snippetQueryService.run(snippet, janName);
    Map<String, RDFNode> oneResult = assertOneResultWithType(result, 6);

    assertThat(oneResult.values()).contains(ResourceFactory.createPlainLiteral("Jeanne"));
  }

  private Map<String, RDFNode> assertOneResultWithType(List<Map<String, RDFNode>> result) {
    return assertOneResultWithType(result, 2);
  }

  private Map<String, RDFNode> assertOneResultWithType(List<Map<String, RDFNode>> result, int size) {
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).size()).isEqualTo(size);
    return result.get(0);
  }

  private RdfStoreService getRdfStoreService(org.springframework.core.io.Resource... resources) {
    JenaModel jenaModel = new JenaModel();
    jenaModel.setResources(Arrays.asList(resources));
    RdfStoreService rdfStoreService = new InMemoryDatabase();
    ((InMemoryDatabase) rdfStoreService).setJenaModel(jenaModel);
    return rdfStoreService;
  }
}

