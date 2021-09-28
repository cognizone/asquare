package zone.cogni.asquare.cube.convertor.inherit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.applicationprofile.json.ApplicationProfileConfig;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.cube.convertor.json.ApplicationProfileToCompactConversionProfile;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfile;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfileToConversionProfile;
import zone.cogni.asquare.cube.convertor.json.ConversionProfile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InheritApplicationProfileTestConfig.class)
public class InheritApplicationProfileTest {

  @Autowired
  private ApplicationProfileConfig applicationProfileConfig;

  @Autowired
  private Resource simpleApplicationProfileResource;

  @Autowired
  private Resource inheritApplicationProfileResource;

  @Autowired
  private Resource inheritWrongApplicationProfileResource;

  private int inheritApplicationProfileTypeCount = 6;

  @Test
  public void read_simple_application_profile() {
    // given
    Resource simpleApplicationProfileResource = this.simpleApplicationProfileResource;

    // when
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer()
                                                                    .apply(simpleApplicationProfileResource);

    // then
    assertThat(applicationProfile.getTypes()).hasSize(3);
  }

  @Test
  public void create_simple_compact_conversion_profile() {
    // given
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer()
                                                                    .apply(simpleApplicationProfileResource);

    // when
    CompactConversionProfile compactConversionProfile = new ApplicationProfileToCompactConversionProfile().apply(applicationProfile);

    // then
    assertThat(compactConversionProfile.getTypes()).hasSize(3);
    assertThat(compactConversionProfile.getById("Animal").getAttributes()).hasSize(1);
    assertThat(compactConversionProfile.getById("Mammal").getAttributes()).hasSize(1);
    assertThat(compactConversionProfile.getById("Limb").getAttributes()).hasSize(0);
  }

  @Test
  public void create_simple_conversion_profile() {
    // given
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer()
                                                                    .apply(simpleApplicationProfileResource);
    CompactConversionProfile compactConversionProfile = new ApplicationProfileToCompactConversionProfile().apply(applicationProfile);

    // when
    ConversionProfile conversionProfile = new CompactConversionProfileToConversionProfile().apply(compactConversionProfile);

    // then
    assertThat(conversionProfile.getTypes()).hasSize(3);
    assertThat(conversionProfile.getTypeFromClassId("Animal").getAttributes()).hasSize(1);
    assertThat(conversionProfile.getTypeFromClassId("Mammal").getAttributes()).hasSize(2);
    assertThat(conversionProfile.getTypeFromClassId("Limb").getAttributes()).hasSize(0);
  }

  @Test
  public void read_inherit_application_profile() {
    // given
    Resource inheritApplicationProfileResource = this.inheritApplicationProfileResource;

    // when
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer()
                                                                    .apply(inheritApplicationProfileResource);

    // then
    assertThat(applicationProfile.getTypes()).hasSize(inheritApplicationProfileTypeCount);
  }

  @Test
  public void create_inherit_compact_conversion_profile() {
    // given
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer()
                                                                    .apply(inheritApplicationProfileResource);

    // when
    CompactConversionProfile compactConversionProfile = new ApplicationProfileToCompactConversionProfile().apply(applicationProfile);

    // then
    assertThat(compactConversionProfile.getTypes()).hasSize(inheritApplicationProfileTypeCount);
    assertThat(compactConversionProfile.getById("Limb").getAttributes()).hasSize(0);
    assertThat(compactConversionProfile.getById("Animal").getAttributes()).hasSize(1);
    assertThat(compactConversionProfile.getById("Mammal").getAttributes()).hasSize(1);
    assertThat(compactConversionProfile.getById("Horse").getAttributes()).hasSize(1);

    CompactConversionProfile.Type snake = compactConversionProfile.getById("Snake");
    assertThat(snake.getAttributes()).hasSize(1);
    assertThat(snake.getById("hasLimbs").isSingle()).isTrue();
  }

  @Test
  public void create_inherit_conversion_profile() {
    // given
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer()
                                                                    .apply(inheritApplicationProfileResource);
    CompactConversionProfile compactConversionProfile = new ApplicationProfileToCompactConversionProfile().apply(applicationProfile);

    // when
    ConversionProfile conversionProfile = new CompactConversionProfileToConversionProfile().apply(compactConversionProfile);

    // then
    assertThat(conversionProfile.getTypes()).hasSize(inheritApplicationProfileTypeCount);
    assertThat(conversionProfile.getTypeFromClassId("Limb").getAttributes()).hasSize(0);

    ConversionProfile.Type animal = conversionProfile.getTypeFromClassId("Animal");
    assertThat(animal.getAttributes()).hasSize(1);
    assertThat(animal.getByAttributeId("hasLimbs").isSingle()).isFalse();

    assertThat(conversionProfile.getTypeFromClassId("Mammal").getAttributes()).hasSize(1);

    ConversionProfile.Type snake = conversionProfile.getTypeFromClassId("Snake");
    assertThat(snake.getAttributes()).hasSize(1);
    assertThat(snake.getByAttributeId("hasLimbs").isSingle()).isTrue();
  }

  @Test
  public void create_inherit_wrong_conversion_profile() {
    // given
    ApplicationProfile applicationProfile = applicationProfileConfig.getDeserializer()
                                                                    .apply(inheritWrongApplicationProfileResource);
    CompactConversionProfile compactConversionProfile = new ApplicationProfileToCompactConversionProfile().apply(applicationProfile);

    // when/then
    Assertions.assertThrows(RuntimeException.class, () -> {
      new CompactConversionProfileToConversionProfile().apply(compactConversionProfile);
    });
  }

}
