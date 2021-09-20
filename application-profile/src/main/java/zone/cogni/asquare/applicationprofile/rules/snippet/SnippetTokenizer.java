package zone.cogni.asquare.applicationprofile.rules.snippet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SnippetTokenizer implements Function<String, List<SnippetTokenizer.Token>> {

  private static Set<Character> separators = Stream.of('_', '~', '.', '-', '!', '&',
                                                       '(', ')', '*', '+', ',', ';',
                                                       '=', '/', '?', '#', '@', '%',
                                                       ' ', ':', '$')
                                                   .collect(Collectors.toSet());

  @SuppressWarnings("Duplicates")
  @Override
  public List<SnippetTokenizer.Token> apply(String string) {
    List<SnippetTokenizer.Token> tokens = new ArrayList<>();


    char c = ':';
    String currentValue = "";

    Token openQuote = null;
    for (int i = 0; i < string.length(); i++) {
      char ch = string.charAt(i);

      if (openQuote == null && separators.contains(Character.valueOf(ch))) {
        addToken(tokens, TokenType.word, currentValue);
        currentValue = "";
        tokens.add(new Token(TokenType.separator, Character.valueOf(ch).toString()));
      }
      else if (ch == '\'') {
        Token quote = null;
        if (string.charAt(i + 1) == '\'' && string.charAt(i + 2) == '\'') {
          quote = new Token(TokenType.single_quote, "''''");
          i += 2;
        }
        else {
          quote = new Token(TokenType.single_quote, "'");
        }

        if (Objects.equals(quote, openQuote)) {
          // closing string => add string and quote
          addToken(tokens, TokenType.string, currentValue);
          currentValue = "";

          tokens.add(quote);
          openQuote = null;
        }
        else if (openQuote != null) {
          // in string, not closing => add value to string
          currentValue += quote.value;
        }
        else {
          // opening string => add quote
          openQuote = quote;
          tokens.add(quote);
        }
      }
      else if (ch == '"') {
        Token quote;
        if (string.charAt(i + 1) == '"' && string.charAt(i + 2) == '"') {
          quote = new Token(TokenType.double_quote, "\"\"\"");
          i += 2;
        }
        else {
          quote = new Token(TokenType.double_quote, "\"");
        }

        if (Objects.equals(quote, openQuote)) {
          // closing string => add string and quote
          addToken(tokens, TokenType.string, currentValue);
          currentValue = "";

          tokens.add(quote);
          openQuote = null;
        }
        else if (openQuote != null) {
          // in string, not closing => add value to string
          currentValue += quote.value;
        }
        else {
          // opening string => add quote
          openQuote = quote;
          tokens.add(quote);
        }
      }
      else {
        currentValue += ch;
      }
    }

    if (!currentValue.isEmpty()) {
      addToken(tokens, TokenType.word, currentValue);
    }

    return tokens;
  }

  private void addToken(List<Token> tokens, TokenType type, String value) {
    if (value.isEmpty()) return;
    tokens.add(new Token(type, value));
  }

  public enum TokenType {
    separator,
    single_quote,
    double_quote,
    word,
    string
  }

  public static class Token {
    private final TokenType type;
    private final String value;

    public Token(TokenType type, String value) {
      this.type = type;
      this.value = value;
    }

    public TokenType getType() {
      return type;
    }

    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "Token{" +
             "type=" + type +
             ", value='" + value + '\'' +
             '}';
    }
  }


}
