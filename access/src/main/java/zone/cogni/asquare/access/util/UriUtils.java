package zone.cogni.asquare.access.util;

import org.springframework.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whaaaat i copied some code from spring....
 */
public class UriUtils {
  public static final String DEFAULT_ENCODING = "UTF-8";

  private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("([^&=]+)(=?)([^&]+)?");

  private static final String SCHEME_PATTERN = "([^:/?#]+):";

  private static final String HTTP_PATTERN = "(?i)(http|https):";

  private static final String USERINFO_PATTERN = "([^@\\[/?#]*)";

  private static final String HOST_IPV4_PATTERN = "[^\\[/?#:]*";

  private static final String HOST_IPV6_PATTERN = "\\[[\\p{XDigit}\\:\\.]*[%\\p{Alnum}]*\\]";

  private static final String HOST_PATTERN = "(" + HOST_IPV6_PATTERN + "|" + HOST_IPV4_PATTERN + ")";

  private static final String PORT_PATTERN = "(\\d*(?:\\{[^/]+?\\})?)";

  private static final String PATH_PATTERN = "([^?#]*)";

  private static final String QUERY_PATTERN = "([^#]*)";

  private static final String LAST_PATTERN = "(.*)";

  // Regex patterns that matches URIs. See RFC 3986, appendix B
  public static final Pattern URI_PATTERN = Pattern.compile(
          "^(" + SCHEME_PATTERN + ")?" + "(//(" + USERINFO_PATTERN + "@)?" + HOST_PATTERN + "(:" + PORT_PATTERN +
          ")?" + ")?" + PATH_PATTERN + "(\\?" + QUERY_PATTERN + ")?" + "(#" + LAST_PATTERN + ")?");

  public static final Pattern HTTP_URL_PATTERN = Pattern.compile(
          "^" + HTTP_PATTERN + "(//(" + USERINFO_PATTERN + "@)?" + HOST_PATTERN + "(:" + PORT_PATTERN + ")?" + ")?" +
          PATH_PATTERN + "(\\?" + LAST_PATTERN + ")?");

  public static boolean isValidHttpUrl(String url) {
    Matcher matcher = HTTP_URL_PATTERN.matcher(url);
    return matcher.matches();
  }

  public static boolean isValidUri(String uri) {
    Matcher matcher = URI_PATTERN.matcher(uri);
    return matcher.matches();
  }

  /**
   * Encode the given source into an encoded String using the rules specified
   * by the given component and with the given options.
   *
   * @param source the source String
   * @param type   the URI component for the source
   * @return the encoded URI
   * @throws IllegalArgumentException when the given value is not a valid URI component
   */
  public static String encodeUriComponent(String source, ComponentType type) {
    return encodeUriComponent(source, DEFAULT_ENCODING, type);
  }

  /**
   * Encode the given source into an encoded String using the rules specified
   * by the given component and with the given options.
   *
   * @param source   the source String
   * @param encoding the encoding of the source String
   * @param type     the URI component for the source
   * @return the encoded URI
   * @throws IllegalArgumentException when the given value is not a valid URI component
   */
  public static String encodeUriComponent(String source, String encoding, ComponentType type) {
    if (source == null) {
      return null;
    }
    Assert.hasLength(encoding, "Encoding must not be empty");
    try {
      byte[] bytes = encodeBytes(source.getBytes(encoding), type);
      return new String(bytes, "US-ASCII");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  /**
   * Decode the given encoded URI component.
   * <ul>
   * <li>Alphanumeric characters {@code "a"} through {@code "z"}, {@code "A"} through {@code "Z"}, and
   * {@code "0"} through {@code "9"} stay the same.</li>
   * <li>Special characters {@code "-"}, {@code "_"}, {@code "."}, and {@code "*"} stay the same.</li>
   * <li>A sequence "{@code %<i>xy</i>}" is interpreted as a hexadecimal representation of the character.</li>
   * </ul>
   *
   * @param source   the encoded String
   * @param encoding the encoding
   * @return the decoded value
   * @throws IllegalArgumentException     when the given source contains invalid encoded sequences
   * @see java.net.URLDecoder#decode(String, String)
   */
  public static String decode(String source, String encoding) {
    if (source == null) {
      return null;
    }
    Assert.hasLength(encoding, "Encoding must not be empty");
    int length = source.length();
    ByteArrayOutputStream bos = new ByteArrayOutputStream(length);
    boolean changed = false;
    for (int i = 0; i < length; i++) {
      int ch = source.charAt(i);
      if (ch == '%') {
        if ((i + 2) < length) {
          char hex1 = source.charAt(i + 1);
          char hex2 = source.charAt(i + 2);
          int u = Character.digit(hex1, 16);
          int l = Character.digit(hex2, 16);
          if (u == -1 || l == -1) {
            throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
          }
          bos.write((char) ((u << 4) + l));
          i += 2;
          changed = true;
        }
        else {
          throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
        }
      }
      else {
        bos.write(ch);
      }
    }
    try {
      return (changed ? new String(bos.toByteArray(), encoding) : source);
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private static byte[] encodeBytes(byte[] source, ComponentType type) {
    Assert.notNull(source, "Source must not be null");
    Assert.notNull(type, "Type must not be null");
    ByteArrayOutputStream bos = new ByteArrayOutputStream(source.length);
    for (byte b : source) {
      if (b < 0) {
        b += 256;
      }
      if (type.isAllowed(b)) {
        bos.write(b);
      }
      else {
        bos.write('%');
        char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
        char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
        bos.write(hex1);
        bos.write(hex2);
      }
    }
    return bos.toByteArray();
  }

  public enum ComponentType {

    SCHEME {
      @Override
      public boolean isAllowed(int c) {
        return isAlpha(c) || isDigit(c) || '+' == c || '-' == c || '.' == c;
      }
    },
    AUTHORITY {
      @Override
      public boolean isAllowed(int c) {
        return isUnreserved(c) || isSubDelimiter(c) || ':' == c || '@' == c;
      }
    },
    USER_INFO {
      @Override
      public boolean isAllowed(int c) {
        return isUnreserved(c) || isSubDelimiter(c) || ':' == c;
      }
    },
    HOST_IPV4 {
      @Override
      public boolean isAllowed(int c) {
        return isUnreserved(c) || isSubDelimiter(c);
      }
    },
    HOST_IPV6 {
      @Override
      public boolean isAllowed(int c) {
        return isUnreserved(c) || isSubDelimiter(c) || '[' == c || ']' == c || ':' == c;
      }
    },
    PORT {
      @Override
      public boolean isAllowed(int c) {
        return isDigit(c);
      }
    },
    PATH {
      @Override
      public boolean isAllowed(int c) {
        return isPchar(c) || '/' == c;
      }
    },
    PATH_SEGMENT {
      @Override
      public boolean isAllowed(int c) {
        return isPchar(c);
      }
    },
    QUERY {
      @Override
      public boolean isAllowed(int c) {
        return isPchar(c) || '/' == c || '?' == c;
      }
    },
    QUERY_PARAM {
      @Override
      public boolean isAllowed(int c) {
        if ('=' == c || '+' == c || '&' == c) {
          return false;
        }
        else {
          return isPchar(c) || '/' == c || '?' == c;
        }
      }
    },
    FRAGMENT {
      @Override
      public boolean isAllowed(int c) {
        return isPchar(c) || '/' == c || '?' == c;
      }
    },
    URI {
      @Override
      public boolean isAllowed(int c) {
        return isUnreserved(c);
      }
    };

    /**
     * Indicates whether the given character is allowed in this URI component.
     *
     * @return {@code true} if the character is allowed; {@code false} otherwise
     */
    public abstract boolean isAllowed(int c);

    /**
     * Indicates whether the given character is in the {@code ALPHA} set.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    protected boolean isAlpha(int c) {
      return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
    }

    /**
     * Indicates whether the given character is in the {@code DIGIT} set.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    protected boolean isDigit(int c) {
      return (c >= '0' && c <= '9');
    }

    /**
     * Indicates whether the given character is in the {@code gen-delims} set.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    protected boolean isGenericDelimiter(int c) {
      return (':' == c || '/' == c || '?' == c || '#' == c || '[' == c || ']' == c || '@' == c);
    }

    /**
     * Indicates whether the given character is in the {@code sub-delims} set.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    protected boolean isSubDelimiter(int c) {
      return ('!' == c || '$' == c || '&' == c || '\'' == c || '(' == c || ')' == c || '*' == c || '+' == c ||
              ',' == c || ';' == c || '=' == c);
    }

    /**
     * Indicates whether the given character is in the {@code reserved} set.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    protected boolean isReserved(int c) {
      return (isGenericDelimiter(c) || isSubDelimiter(c));
    }

    /**
     * Indicates whether the given character is in the {@code unreserved} set.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    protected boolean isUnreserved(int c) {
      return (isAlpha(c) || isDigit(c) || '-' == c || '.' == c || '_' == c || '~' == c);
    }

    /**
     * Indicates whether the given character is in the {@code pchar} set.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    protected boolean isPchar(int c) {
      return (isUnreserved(c) || isSubDelimiter(c) || ':' == c || '@' == c);
    }
  }
}
