package zone.cogni.asquare.applicationprofile.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetTest {
  private SnippetRegistry snippetRegistry = new SnippetRegistry();

  @Test
  void validate_person_findByName() {
    Snippet snippet = snippetRegistry.getSnippet("person.findByName");

    assertThat(snippet.getInputParameters()).containsExactlyInAnyOrder("personName");
    assertThat(snippet.getOutputParameters()).containsExactlyInAnyOrder("person");
    assertThat(snippet.getNamespacePrefixes()).containsExactlyInAnyOrder("rdfs");
  }

  @Test
  void validate_person_spouse() {
    Snippet snippet = snippetRegistry.getSnippet("person.spouse");

    assertThat(snippet.getInputParameters()).containsExactlyInAnyOrder("person");
    assertThat(snippet.getOutputParameters()).containsExactlyInAnyOrder("spouse");
    assertThat(snippet.getNamespacePrefixes()).containsExactlyInAnyOrder("czonto");
  }

  @Test
  void validate_person_name() {
    Snippet snippet = snippetRegistry.getSnippet("person.name");

    assertThat(snippet.getInputParameters()).containsExactlyInAnyOrder("person");
    assertThat(snippet.getOutputParameters()).containsExactlyInAnyOrder("personName");
    assertThat(snippet.getNamespacePrefixes()).containsExactlyInAnyOrder("rdfs");
  }

  @Test
  void validate_person_spouse_name() {
    Snippet snippet = snippetRegistry.getSnippet("person.spouseName");

    assertThat(snippet.getInputParameters()).containsExactlyInAnyOrder("personName");
    assertThat(snippet.getOutputParameters()).containsExactlyInAnyOrder("person", "spouse", "spouseName");
    assertThat(snippet.getNamespacePrefixes()).containsExactlyInAnyOrder("rdfs", "czonto");
  }

  @Test
  void prefix_with_dash() {
    Snippet snippet = snippetRegistry.getSnippet("prefix-with-dash");

    assertThat(snippet.getInputParameters()).containsExactlyInAnyOrder("person");
    assertThat(snippet.getOutputParameters()).containsExactlyInAnyOrder("spouse");
    assertThat(snippet.getNamespacePrefixes()).containsExactlyInAnyOrder("cz-onto");
  }
}
