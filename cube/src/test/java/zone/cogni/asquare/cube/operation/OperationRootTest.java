package zone.cogni.asquare.cube.operation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationRootTest {

  @SuppressWarnings("CodeBlock2Expr")
  @Test
  public void wrong_prefixes() {
    // given
    List<InputStreamSource> files = Arrays.asList(
      new ClassPathResource("operation/operation-root/wrong-prefix-1.json5"),
      new ClassPathResource("operation/operation-root/wrong-prefix-2.json5")
    );

    // when
    assertThatThrownBy(() -> {
      OperationRoot.load(files);
    }) // then
       .isInstanceOf(RuntimeException.class)
       .hasMessage("Some prefixes in files have conflicting namespaces: [person]");
  }

  @SuppressWarnings("CodeBlock2Expr")
  @Test
  public void wrong_operation_groups() {
    // given
    List<InputStreamSource> files = Arrays.asList(
      new ClassPathResource("operation/operation-root/wrong-operation-group-1.json5"),
      new ClassPathResource("operation/operation-root/wrong-operation-group-2.json5")
    );

    // when
    assertThatThrownBy(() -> {
      OperationRoot.load(files);
    }) // then
       .isInstanceOf(RuntimeException.class)
       .hasMessage("Some operation groups are defined more than once: [two]");
  }

  @Test
  public void merge_operation_files_success() {
    // given
    List<InputStreamSource> files = Arrays.asList(
      new ClassPathResource("operation/operation-root/operations-1.json5"),
      new ClassPathResource("operation/operation-root/operations-2.json5")
    );

    // when
    OperationRoot root = OperationRoot.load(files);

    // then
    assertThat(root.getPrefixes().size()).isEqualTo(3);
    assertThat(root.getOperationGroups().size()).isEqualTo(1);
    assertThat(root.getOperationGroups().get(0).getOperationGroups().size()).isEqualTo(4);
  }

  @Test
  public void merge_operation_files_passes_validation() {
    // given
    List<InputStreamSource> files = Arrays.asList(
      new ClassPathResource("operation/operation-root/operations-1.json5"),
      new ClassPathResource("operation/operation-root/operations-2.json5")
    );

    OperationRoot root = OperationRoot.load(files);

    // when
    root.validate();

    // then
    // don't crash
  }

  @Test
  public void merge_operation_wrong_path() {
    // given
    List<InputStreamSource> files = Arrays.asList(
      new ClassPathResource("operation/operation-root/operations-wrong-path-1.json5"),
      new ClassPathResource("operation/operation-root/operations-wrong-path-2.json5")
    );

    OperationRoot root = OperationRoot.load(files);

    // when
    assertThatThrownBy(root::validate)
      // then
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("invalid requires")
      .hasMessageContaining("cannot find operation 'is-wrong-path'");
  }
}