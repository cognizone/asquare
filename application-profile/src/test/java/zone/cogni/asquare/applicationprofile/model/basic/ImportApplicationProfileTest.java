package zone.cogni.asquare.applicationprofile.model.basic;

import io.vavr.collection.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.model.basic.def.ApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.model.basic.def.BasicApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.model.basic.def.MultiApplicationProfileDef;
import zone.cogni.asquare.applicationprofile.rules.ClassId;
import zone.cogni.asquare.applicationprofile.rules.Range;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ApplicationProfileConfig.class)
class ImportApplicationProfileTest {

  @Autowired
  private ApplicationProfileConfig applicationProfileConfig;

  @Test
  void test_simple_application_profile() {
    // given
    Resource resource = new ClassPathResource("model/basic/imported.ap.json");

    // when
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer().apply(resource);

    // then
    assertEquals(2, applicationProfile.getTypes().size());
    assertTrue(applicationProfile.hasType("Organization"));
    assertTrue(applicationProfile.hasType("RegisteredOrganization"));

    assertTrue(applicationProfile.getInternalClassChain()
                                 .containsAll(Stream.of(BasicApplicationProfile.class,
                                                        BasicApplicationProfileDef.class).collect(Collectors.toList())
                                 ));
    assertEquals(2, applicationProfile.getInternalClassChain().size());
  }

  @Test
  void test_import_application_profile() {
    // given
    Resource resource = new ClassPathResource("model/basic/importing.ap.json");

    // when
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer().apply(resource);

    // then
    assertTrue(applicationProfile.hasType("Car"));
    assertTrue(applicationProfile.hasType("Organization"));
    assertTrue(applicationProfile.hasType("RegisteredOrganization"));
    assertEquals(3, applicationProfile.getTypes().size());

    // only multi if there are imports!
    assertTrue(applicationProfile.getInternalClassChain()
                                 .containsAll(Stream.of(BasicApplicationProfile.class,
                                                        MultiApplicationProfileDef.class,
                                                        BasicApplicationProfileDef.class).collect(Collectors.toList())
                                 ));
    assertEquals(3, applicationProfile.getInternalClassChain().size());
  }

  @Test
  void test_access_imported_type_via_range() {
    // given
    Resource resource = new ClassPathResource("model/basic/importing.ap.json");

    // when
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer().apply(resource);
    ApplicationProfile.Type car = applicationProfile.getType("Car");
    ApplicationProfile.Attribute owner = car.getAttribute("owner");
    Range range = owner.getRule(Range.class).get();
    ClassId rangeRule = (ClassId) range.getValue();
    String organization = rangeRule.getValue();

    // then
    assertTrue(applicationProfile.hasType(organization));
    assertTrue(car.getApplicationProfile().hasType(organization));
    assertEquals(applicationProfile,
                 car.getApplicationProfile(),
                 "application profile from file and via car should be equals");
    assertSame(applicationProfile.getApplicationProfileDef(),
               car.getApplicationProfile().getApplicationProfileDef(),
               "application profile definition from file and via car should be same");

    ApplicationProfileDef rootDefinition = applicationProfile.getApplicationProfileDef().getRootDefinition();
    List<ApplicationProfileDef> imports = rootDefinition.getImports();
    assertEquals(1, imports.size());
    assertSame(rootDefinition,
               imports.get(0).getTopDefinition().getRootDefinition());
    assertSame(rootDefinition,
               imports.get(0).getParentDefinition().getRootDefinition());
  }

}
