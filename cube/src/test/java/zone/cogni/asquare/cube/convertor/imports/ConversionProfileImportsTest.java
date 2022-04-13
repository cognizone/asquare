package zone.cogni.asquare.cube.convertor.imports;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import zone.cogni.asquare.cube.convertor.json.CompactConversionProfile;
import zone.cogni.asquare.cube.convertor.json.CollapsedImportsCompactConversionProfile;

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

}
