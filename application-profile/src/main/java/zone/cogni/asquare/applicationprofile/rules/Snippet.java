package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.Rule;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;
import zone.cogni.asquare.applicationprofile.rules.snippet.SnippetTokenizer;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Snippet extends SingleValueRule<String> {

  private final Set<String> namespacePrefixes = new HashSet<>();
  private final Set<String> inputParameters = new HashSet<>();
  private final Set<String> outputParameters = new HashSet<>();

  public Snippet() {
  }

  public Snippet(String value) {
    setValue(value);
  }

  @Override
  public void setValue(String value) {
    super.setValue(value);
    init();
  }

  private void init() {
    List<SnippetTokenizer.Token> tokens = new SnippetTokenizer().apply(getValue());
    initNamespacePrefixes(tokens);
    initInputParameters(tokens);
    initOutputParameters(tokens);
  }

  private void initNamespacePrefixes(List<SnippetTokenizer.Token> tokens) {
    for (int i = 0; i < tokens.size() - 1; i++) {
      SnippetTokenizer.Token spaceToken = tokens.get(i);
      if (spaceToken.getType() != SnippetTokenizer.TokenType.separator) continue;
      if (!spaceToken.getValue().equals(" ")) continue;

      getNamespaceFrom(tokens, i+1).ifPresent(string -> {
        if (!string.startsWith("<")) {
          namespacePrefixes.add(string);
        }
      });
    }
  }

  private Optional<String> getNamespaceFrom(List<SnippetTokenizer.Token> tokens, int start) {
    for (int j = start; j < tokens.size() - 1; j++) {
      SnippetTokenizer.Token token = tokens.get(j);
      if (token.getType().equals(SnippetTokenizer.TokenType.separator)
          && token.getValue().equals(":")) return Optional.of(substring(tokens, start, j));

      if (token.getType().equals(SnippetTokenizer.TokenType.word)) continue;
      if (token.getType().equals(SnippetTokenizer.TokenType.separator)
          && token.getValue().equals("-")) continue;

      return Optional.empty();
    }

    return Optional.empty();
  }

  private String substring(List<SnippetTokenizer.Token> tokens, int start, int end) {
    StringBuilder result = new StringBuilder();
    for (int i = start; i < end; i++) {
      SnippetTokenizer.Token token =  tokens.get(i);
      result.append(token.getValue());
    }
    return result.toString();
  }

  private void initInputParameters(List<SnippetTokenizer.Token> tokens) {
    for (int i = 0; i < tokens.size() - 1; i++) {
      addParameter(inputParameters, tokens, i, "$");
    }
  }

  private void initOutputParameters(List<SnippetTokenizer.Token> tokens) {
    for (int i = 0; i < tokens.size() - 1; i++) {
      addParameter(this.outputParameters, tokens, i, "?");
    }
  }

  private void addParameter(Set<String> parameters,
                            List<SnippetTokenizer.Token> tokens,
                            int position,
                            String separatorCharacter) {
    SnippetTokenizer.Token variableToken = tokens.get(position);
    if (variableToken.getType() != SnippetTokenizer.TokenType.separator) return;
    if (!variableToken.getValue().equals(separatorCharacter)) return;

    SnippetTokenizer.Token word = tokens.get(position + 1);
    if (word.getType() != SnippetTokenizer.TokenType.word) return;

    parameters.add(word.getValue());
  }

  public Set<String> getInputParameters() {
    return Collections.unmodifiableSet(inputParameters);
  }

  public Set<String> getOutputParameters() {
    return Collections.unmodifiableSet(outputParameters);
  }

  public Set<String> getNamespacePrefixes() {
    return Collections.unmodifiableSet(namespacePrefixes);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public Rule copy() {
    return new Snippet(getValueCopy());
  }
}
