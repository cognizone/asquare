package zone.cogni.asquare.cube.convertor.data2shacl;

import com.google.common.collect.ImmutableList;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import zone.cogni.asquare.cube.convertor.json.CollapsedImportsCompactConversionProfile;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfile;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfileToConversionProfile;
import zone.cogni.asquare.cube.convertor.json.ConversionProfile;
import zone.cogni.asquare.cube.pagination.PaginatedQuery;
import zone.cogni.asquare.cube.spel.SpelService;
import zone.cogni.asquare.triplestore.RdfStoreService;
import zone.cogni.asquare.triplestore.jenamemory.InternalRdfStoreService;
import zone.cogni.sem.jena.JenaUtils;

import java.util.HashMap;
import java.util.List;

import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;
import static org.assertj.core.api.Assertions.assertThat;

public class ShaclGeneratorTest {

  private final ShaclGenerator shaclGenerator = new ShaclGenerator(new SpelService(),
                                                                   new PaginatedQuery(1000));

  @Test
  public void person_test() {
    // TODO not everything is tested here: basic tests still need to be done
    // - namespace
    // - NodeShape, PropertyShape
    // - sh:datatype, minCount, maxCount
    // - sh:nodeKind, sh:targetClass, sh:path, sh:class
    Configuration configuration = getConfiguration();

    RdfStoreService rdfStore = getDataModel("person.ttl");

    Model shacl = shaclGenerator.generate(configuration, getPrefixes(), rdfStore);
    shacl.write(System.out, "ttl");
  }

  @Test
  public void person_or_test() {
    // given
    Configuration configuration = getConfiguration();
    RdfStoreService rdfStore = getDataModel("person-or.ttl");

    // when
    Model shacl = shaclGenerator.generate(configuration, getPrefixes(), rdfStore);

    // then
    // check if there is a property with sh:or
    Resource talksProp = ResourceFactory.createResource("http://demo.com/shacl/Person/talks");
    List<RDFNode> shOr = shacl.listObjectsOfProperty(talksProp, SHACLM.or).toList();
    assertThat(shOr.size()).isEqualTo(1);

    // check if the sh:or has 2 values
    RDFList rdfList = shacl.listObjectsOfProperty(talksProp, SHACLM.or).next().as(RDFList.class);

    Resource booleanTalks = ResourceFactory.createResource("http://demo.com/shacl/Person/talks/boolean");
    Resource stringTalks = ResourceFactory.createResource("http://demo.com/shacl/Person/talks/string");
    assertThat(rdfList.asJavaList()).containsExactlyInAnyOrder(booleanTalks, stringTalks);

    // check booleanTalks
    assertThat(shacl.contains(booleanTalks, RDF.type, SHACLM.PropertyShape)).isTrue();
    assertThat(shacl.contains(booleanTalks, SHACLM.datatype, XSD.xboolean)).isTrue();

    shacl.write(System.out, "ttl");
  }

  @Test
  public void concept_ignored_test() {
    // given
    Configuration configuration = getConfiguration();
    RdfStoreService rdfStore = getDataModel("concept-ignored.ttl");

    // when
    Model shacl = shaclGenerator.generate(configuration, getPrefixes(), rdfStore);

    // then
    List<Resource> nodeShapes = shacl.listSubjectsWithProperty(RDF.type, SHACLM.NodeShape).toList();
    Resource conceptNodeShape = ResourceFactory.createResource("http://demo.com/shacl/Concept");
    assertThat(nodeShapes).containsExactlyInAnyOrder(conceptNodeShape);
  }

  @Test
  public void concept_language_in_and_unique_lang_test() {
    // given
    Configuration configuration = getConfiguration();
    RdfStoreService rdfStore = getDataModel("concept-language-in-and-unique-lang.ttl");

    // when
    Model shacl = shaclGenerator.generate(configuration, getPrefixes(), rdfStore);
    shacl.write(System.out, "ttl");

    // then
    // check shapes
    assertThat(shacl.listSubjectsWithProperty(RDF.type, SHACLM.NodeShape).toList().size()).isEqualTo(1);
    assertThat(shacl.listSubjectsWithProperty(RDF.type, SHACLM.PropertyShape).toList().size()).isEqualTo(1);

    // check datatype, minCount, uniqueLang
    Resource prefLabel = ResourceFactory.createResource("http://demo.com/shacl/Concept/prefLabel");
    assertThat(shacl.contains(prefLabel, SHACLM.datatype, RDF.langString)).isTrue();
    assertThat(shacl.contains(prefLabel, SHACLM.minCount, createTypedLiteral(1))).isTrue();
    assertThat(shacl.contains(prefLabel, SHACLM.uniqueLang, createTypedLiteral(true))).isTrue();

    // check languageIn
    RDFList rdfList = shacl.listObjectsOfProperty(prefLabel, SHACLM.languageIn).next().as(RDFList.class);
    assertThat(rdfList.asJavaList()).containsExactlyInAnyOrder(
            ResourceFactory.createPlainLiteral("en"),
            ResourceFactory.createPlainLiteral("nl"),
            ResourceFactory.createPlainLiteral("fr")
    );
  }

  @Test
  public void concept_unique_lang_false_test() {
    // given
    Configuration configuration = getConfiguration();
    RdfStoreService rdfStore = getDataModel("concept-unique-lang-false.ttl");

    // when
    Model shacl = shaclGenerator.generate(configuration, getPrefixes(), rdfStore);
    shacl.write(System.out, "ttl");

    // then
    // check uniqueLang does not exist!
    Resource prefLabel = ResourceFactory.createResource("http://demo.com/shacl/Concept/prefLabel");
    assertThat(shacl.contains(prefLabel, SHACLM.uniqueLang, (RDFNode) null)).isFalse();
  }

  private RdfStoreService getDataModel(String file) {
    String folder = "convertor/data2shacl/";
    ClassPathResource resource = new ClassPathResource(folder + file);
    Model model = JenaUtils.read(resource, null, "ttl");
    return new InternalRdfStoreService(model);
  }

  private HashMap<String, String> getPrefixes() {
    HashMap<String, String> result = new HashMap<>();
    result.put("sh", SHACLM.NS);
    return result;
  }

  private Configuration getConfiguration() {
    Configuration configuration = new Configuration();
    configuration.setShapesNamespace("http://demo.com/shacl/");
    configuration.setIgnoredClasses(ImmutableList.of("http://data.europa.eu/eli/ontology#Language"));
    return configuration;
  }

  private ConversionProfile getShaczProfile() {
    InputStreamSource shaczResource = new ClassPathResource("convertor/data2shacl/shacz/shacz.profile.json");
    CompactConversionProfile compactConversionProfile = CompactConversionProfile.read(shaczResource);
    CompactConversionProfile collapsedConversionProfile = new CollapsedImportsCompactConversionProfile().apply(compactConversionProfile);
    ConversionProfile conversionProfile = new CompactConversionProfileToConversionProfile().apply(collapsedConversionProfile);
    return conversionProfile;
  }

}
