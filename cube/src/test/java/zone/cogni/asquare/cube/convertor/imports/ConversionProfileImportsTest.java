package zone.cogni.asquare.cube.convertor.imports;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.convertor.json.CollapsedImportsCompactConversionProfile;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfile;

import static org.assertj.core.api.Assertions.assertThat;

public class ConversionProfileImportsTest {

  @Test
  public void multi_level_imports_load() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/multi_level/mammal-conversion-profile.json");

    // when
    CompactConversionProfile profile = CompactConversionProfile.read(resource);

    // then
    assertThat(profile.getTypes()).hasSize(1);
    assertThat(profile.getImports()).hasSize(1);
    assertThat(profile.getImports()).contains("convertor/imports/multi_level/animal-conversion-profile.json");
  }

  @Test
  public void multi_level_imports_merge() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/multi_level/dog-conversion-profile.json");
    CompactConversionProfile dogProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(dogProfile);

    // then
    assertThat(profile.getPrefixes()).isNull();
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getTypes()).hasSize(3);
  }

  @Test
  public void multi_level_imports_prefix() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/prefixes/dog-conversion-profile.json");
    CompactConversionProfile dogProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(dogProfile);

    // then
    assertThat(profile.getTypes()).hasSize(3);
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getPrefixes()).hasSize(3);
    assertThat(profile.getPrefixes().keySet()).containsExactly("a", "b", "c");
  }

  @Test
  public void multi_level_imports_prefix_duplicate() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/prefixes/cat-conversion-profile.json");
    CompactConversionProfile dogProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(dogProfile);

    // then
    assertThat(profile.getTypes()).hasSize(3);
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getPrefixes()).hasSize(3);
    assertThat(profile.getPrefixes().keySet()).containsExactly("a", "b", "c");
  }

  @Test
  public void multi_level_imports_prefix_duplicate_problem() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/prefixes/mouse-conversion-profile.json");
    CompactConversionProfile dogProfile = CompactConversionProfile.read(resource);

    // when
    Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
      new CollapsedImportsCompactConversionProfile().apply(dogProfile);
    });

    // then
    assertThat(exception.getMessage())
            .contains("'a'")
            .contains("'http://demo.com/a#'")
            .contains("'http://demo.com/error#'");
  }

  @Test
  public void imports_same_type_same_properties() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/types/same-properties-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(animalProfile);

    // then
    assertThat(profile.getPrefixes()).isNull();
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getTypes()).hasSize(2);

  }

  @Test
  public void imports_same_type_no_properties() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/types/no-properties-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(animalProfile);

    // then
    assertThat(profile.getPrefixes()).isNull();
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getTypes()).hasSize(2);
  }

  @Test
  public void imports_same_type_different_properties() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/types/different-properties-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
      new CollapsedImportsCompactConversionProfile().apply(animalProfile);
    });

    assertThat(exception.getMessage())
            .contains("'Mammal'")
            .contains("different fields");
  }

  @Test
  public void imports_same_type_no_superclass() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/types/no-superclass-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(animalProfile);

    // then
    assertThat(profile.getPrefixes()).isNull();
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getTypes()).hasSize(2);
  }

  @Test
  public void imports_same_attribute_different_property() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/attributes/different-property-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
      new CollapsedImportsCompactConversionProfile().apply(animalProfile);
    });

    // then
    assertThat(exception.getMessage())
            .contains("'Animal.name'")
            .contains("different properties");
  }

  @Test
  public void imports_same_attribute_different_inverse() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/attributes/different-inverse-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
      new CollapsedImportsCompactConversionProfile().apply(animalProfile);
    });

    // then
    assertThat(exception.getMessage())
            .contains("'Animal.name'")
            .contains("different inverses");
  }

  @Test
  public void imports_same_attribute_multiple_to_single() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/attributes/multiple-upper-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(animalProfile);

    // then
    assertThat(profile.getPrefixes()).isNull();
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getTypes()).hasSize(1);

    CompactConversionProfile.Type animalType = profile.getById("Animal");
    assertThat(animalType.getAttributes()).hasSize(1);
    assertThat(animalType.getById("name").isSingle()).isTrue();
  }

  @Test
  public void imports_same_attribute_single_to_multiple() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/attributes/single-upper-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(animalProfile);

    // then
    assertThat(profile.getPrefixes()).isNull();
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getTypes()).hasSize(1);

    CompactConversionProfile.Type animalType = profile.getById("Animal");
    assertThat(animalType.getAttributes()).hasSize(1);
    assertThat(animalType.getById("name").isSingle()).isTrue();
  }

  @Test
  public void imports_same_attribute_type_mix_to_datatype() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/attributes/type-mix-upper-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(animalProfile);

    // then
    assertThat(profile.getPrefixes()).isNull();
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getTypes()).hasSize(1);

    CompactConversionProfile.Type animalType = profile.getById("Animal");
    assertThat(animalType.getAttributes()).hasSize(1);
    assertThat(animalType.getById("name").getType()).isEqualTo(CompactConversionProfile.Attribute.Type.datatype);
  }

  @Test
  public void imports_same_attribute_type_datatype_to_mix() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/attributes/type-datatype-upper-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    CompactConversionProfile profile = new CollapsedImportsCompactConversionProfile().apply(animalProfile);

    // then
    assertThat(profile.getPrefixes()).isNull();
    assertThat(profile.getImports()).isNull();
    assertThat(profile.getTypes()).hasSize(1);

    CompactConversionProfile.Type animalType = profile.getById("Animal");
    assertThat(animalType.getAttributes()).hasSize(1);
    assertThat(animalType.getById("name").getType()).isEqualTo(CompactConversionProfile.Attribute.Type.datatype);

  }

  @Test
  public void imports_same_attribute_type_datatype_to_object() {
    // given
    Resource resource = new ClassPathResource("convertor/imports/attributes/type-error-upper-conversion-profile.json");
    CompactConversionProfile animalProfile = CompactConversionProfile.read(resource);

    // when
    Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
      new CollapsedImportsCompactConversionProfile().apply(animalProfile);
    });

    // then
    assertThat(exception.getMessage())
            .contains("'Animal.name'")
            .contains("non-overlapping types");
  }

}
