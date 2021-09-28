package zone.cogni.asquare.applicationprofile.owl.owl2ap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileSerializer;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.prefix.PrefixCcService;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Datatype;
import zone.cogni.asquare.applicationprofile.rules.Range;
import zone.cogni.sem.jena.JenaUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static zone.cogni.asquare.applicationprofile.owl.owl2ap.Check.checkRuleClass;
import static zone.cogni.asquare.applicationprofile.owl.owl2ap.Check.rule;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PrefixCcService.class)
public class Owl2SquareOwlTest {

  @Autowired
  private PrefixCcService prefixCcService;

  @Test
  public void owl_without_ontology() {
    Resource owlModel = new ClassPathResource("owl2ap/without_ontology.owl.ttl");

    assertThrows(IllegalStateException.class, () -> readOwlResource(owlModel));
  }

  @Test
  public void big_owl_reader_test() {
    Resource owlModel = new ClassPathResource("owl2ap/big.owl.ttl");

    ApplicationProfileDef applicationProfile = readOwlResource(owlModel);

    JsonNode apJson = new ApplicationProfileSerializer().apply(applicationProfile);
    System.out.println("applicationProfile = \n" + toIndentedString(apJson));


    Check check = new Check(applicationProfile);

    evaluateEmptyRdfsClass(check);
    evaluateEmptyOwlClass(check);
    evaluateSubclassOfEmptyRdfsClass(check);
    evaluateSubclassOfEmptyOwlClass(check);

    evaluateOwlThing(check);
    evaluateBasicOwlClass(check);
    evaluateRangeClass(check);


    // TODO OwlThing ?? or Thing?
    // TODO missung subClassOf if more than one
  }

  private void evaluateEmptyRdfsClass(Check check) {
    check
            .hasType("EmptyRdfsClass")
            .hasRuleOfType("EmptyRdfsClass", "rdfType")
            .extraSize("EmptyRdfsClass", 0);

  }

  private void evaluateEmptyOwlClass(Check check) {
    check
            .hasType("EmptyOwlClass")
            .hasRuleOfType("EmptyOwlClass", "rdfType")
            .extraSize("EmptyOwlClass", 1)
            .hasExtraProperty("EmptyOwlClass", "rdfType");
  }

  private void evaluateSubclassOfEmptyRdfsClass(Check check) {
    check
            .hasType("SubclassOfEmptyRdfsClass")
            .hasRuleOfType("SubclassOfEmptyRdfsClass", "subClassOf")
            .hasSubClassOf("SubclassOfEmptyRdfsClass", "OwlThing")
            .hasSubClassOf("SubclassOfEmptyRdfsClass", "EmptyRdfsClass")
    ;
  }

  private void evaluateSubclassOfEmptyOwlClass(Check check) {
    check
            .hasType("SubclassOfEmptyOwlClass")
            .hasRuleOfType("SubclassOfEmptyOwlClass", "subClassOf")
            .hasSubClassOf("SubclassOfEmptyOwlClass", "OwlThing")
            .hasSubClassOf("SubclassOfEmptyOwlClass", "EmptyOwlClass")
    ;
  }

  private void evaluateOwlThing(Check check) {
    check
            .hasType("OwlThing")
            .hasAttribute("OwlThing", "noDomainProperty")
            .hasAttribute("OwlThing", "noDomainObjectProperty")
            .hasAttribute("OwlThing", "noDomainDatatypeProperty")
            .hasAttribute("OwlThing", "noDomainAnnotationProperty")
            .attributeRuleSize("OwlThing", "noDomainProperty", 0)
            .attributeRuleSize("OwlThing", "noDomainObjectProperty", 0)
            .attributeRuleSize("OwlThing", "noDomainDatatypeProperty", 0)
            .attributeRuleSize("OwlThing", "noDomainAnnotationProperty", 0)
    ;
  }

  private void evaluateBasicOwlClass(Check check) {
    check
            .hasType("BasicOwlClass")
            .hasAttribute("BasicOwlClass", "basicDomainProperty")
            .hasAttribute("BasicOwlClass", "basicDomainObjectProperty")
            .hasAttribute("BasicOwlClass", "basicDomainDatatypeProperty")
            .hasAttribute("BasicOwlClass", "basicDomainAnnotationProperty")
    ;


  }

  private void evaluateRangeClass(Check check) {
    check
            .hasAttribute("RangeClass", "rangeClassProperty")
            .hasRuleOfType("RangeClass", "rangeClassProperty", "range")
            .hasAttribute("RangeClass", "rangeDatatypeProperty")
            .hasRuleOfType("RangeClass", "rangeDatatypeProperty", "range")
    // TODO this stuff crashes
//            .hasAttribute("RangeClass", "rangeComplementOfProperty")
//            .hasRuleOfType("RangeClass", "rangeComplementOfProperty", "range")
    ;

    Range rangeClass = rule("RangeClass", "rangeClassProperty", Range.class).apply(check);
    checkRuleClass(rangeClass.getValue(), ClassId.class);

    Range rangeDatatype = rule("RangeClass", "rangeDatatypeProperty", Range.class).apply(check);
    checkRuleClass(rangeDatatype.getValue(), Datatype.class);

//    Range rangeComplementOf = rule("RangeClass", "rangeComplementOfProperty", Range.class).apply(check);
//    checkRuleClass(rangeComplementOf.getValue(), Not.class);
//    checkRuleClass(((Not) rangeComplementOf.getValue()).getValue(), Datatype.class);

  }

  private String toIndentedString(JsonNode jsonNode) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      Object json = mapper.readValue(jsonNode.toString(), Object.class);
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ApplicationProfileDef readOwlResource(Resource owlModel) {
    Model model = JenaUtils.read(owlModel);
    return new Owl2SquareOwl(prefixCcService).apply(model);
  }

}
