package zone.cogni.asquare.cube.json5;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * <p>
 * A Jackson parser which can read a subset of the JSON5 standard.
 * </p>
 * <p>
 * JSON5 a more permissive, somewhat more readable superset of JSON.
 * See <a href="https://www.json5.org">json5.org</a> for more details.
 * </p>
 * <p>
 * JSON5 features
 * <ul>
 *   <li>ecmascript identifier keys</li>
 *   <li>single trailing comma</li>
 *   <li>single quoted string</li>
 *   <li>multiline string using escapes</li>
 *   <li>allow character escapes</li>
 *   <li><strike>hexadecimal numbers</strike></li>
 *   <li><strike>leading or trailing decimal point</strike></li>
 *   <li>negative and positive infinity and NaN</li>
 *   <li><strike>explicit plus sign</strike></li>
 *   <li>single and multiline comments</li>
 *   <li><strike>additional whitespace</strike></li>
 * </ul>
 * </p>
 */
public class Json5Light {

  /**
   * @return a builder which we can use to configure some of the JSON5 features
   */
  public static Builder builder() {
    return new Builder();
  }


  /**
   * <p>
   * JSON5 features which are enabled via Jackson are:
   * ecmascript identifiers, single trailing comma, single quoted strings,
   * multiline strings, character escapes, non-numeric numbers and comments.
   * </p>
   * <p>
   * In case of multiline strings you MUST use double quotes e.g. <code>"multiline string here"</code>
   * </p>
   *
   * @return a Jackson {@link com.fasterxml.jackson.databind.ObjectMapper ObjectMapper} with
   * as many features as possible enabled.
   */
  public static ObjectMapper getJson5Mapper() {
    return new Builder().allowEcmascriptIdentifier()
                        .allowSingleTrailingComma()
                        .allowSingleQuotedStrings()
                        .allowMultilineStrings()
                        .allowCharacterEscapes()
                        .allowNonNumericNumbers()
                        .allowComments()
                        .build();
  }

  public static class Builder {

    private final JsonMapper.Builder builder = JsonMapper.builder();

    public Builder allowEcmascriptIdentifier() {
      builder.enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES);
      return this;
    }

    public Builder allowSingleTrailingComma() {
      builder.enable(JsonReadFeature.ALLOW_TRAILING_COMMA);
      return this;
    }

    public Builder allowSingleQuotedStrings() {
      builder.enable(JsonReadFeature.ALLOW_SINGLE_QUOTES);
      return this;
    }

    /**
     * Not sure if it 100% follow specifications, but if not it is an approximation.
     * Builder returned only allows multiline strings in case they are using double-quotes e.g. <code>"example"</code>
     */
    public Builder allowMultilineStrings() {
      builder.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
             .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER);
      return this;
    }

    /**
     * Not sure if it 100% follow specifications, but if not it is an approximation.
     */
    public Builder allowCharacterEscapes() {
      builder.enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER);
      return this;
    }

    public Builder allowNonNumericNumbers() {
      builder.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS);
      return this;
    }

    public Builder allowComments() {
      builder.enable(JsonReadFeature.ALLOW_JAVA_COMMENTS);
      return this;
    }

    public ObjectMapper build() {
      return builder.build();
    }

  }

}