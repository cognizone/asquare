package zone.cogni.asquare.cube.convertor.data2shacl;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.access.shacl.Shacl;
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

public class ShaclGeneratorTest {

  private final ShaclGenerator shaclGenerator = new ShaclGenerator(new SpelService(),
                                                                   new PaginatedQuery(1000));

  @Test
  public void person_test() {
    Configuration configuration = getConfiguration();

    RdfStoreService rdfStore = getDataModel("person.ttl");

    Model shacl = shaclGenerator.generate(configuration, getPrefixes(), rdfStore);
    shacl.write(System.out, "ttl");
  }

  private RdfStoreService getDataModel(String file) {
    String folder = "convertor/data2shacl/";
    ClassPathResource resource = new ClassPathResource(folder + file);
    Model model = JenaUtils.read(resource, null, "ttl");
    return new InternalRdfStoreService(model);
  }

  private HashMap<String, String> getPrefixes() {
    HashMap<String, String> result = new HashMap<>();
    result.put("sh", Shacl.NS);
    return result;
  }

  private Configuration getConfiguration() {
    Configuration configuration = new Configuration();
    configuration.setShapesNamespace("http://demo.com/shacl/");
    configuration.setIgnoredClasses(List.of("http://www.w3.org/2004/02/skos/core#Concept",
                                            "http://data.legilux.public.lu/resource/ontology/jolux#Work",
                                            "http://data.legilux.public.lu/resource/ontology/jolux#WorkAtOj",
                                            "http://publications.europa.eu/resource/authority/language",
                                            "http://data.europa.eu/eli/ontology#Language"));
    return configuration;
  }

  private ConversionProfile getShaczProfile() {
    Resource shaczResource = new ClassPathResource("convertor/data2shacl/shacz/shacz.profile.json");
    CompactConversionProfile compactConversionProfile = CompactConversionProfile.read(shaczResource);
    CompactConversionProfile collapsedConversionProfile = new CollapsedImportsCompactConversionProfile().apply(compactConversionProfile);
    ConversionProfile conversionProfile = new CompactConversionProfileToConversionProfile().apply(collapsedConversionProfile);
    return conversionProfile;
  }

}
