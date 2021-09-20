package zone.cogni.asquare.access.util;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

public class PropertyPathUriMapper {
  private static final String baseUri = "http://asquare.com/onto/propertypath/";

  public static String getUri(String propertyPath) {
    return baseUri + UriUtils.encodeUriComponent(propertyPath, UriUtils.ComponentType.PATH_SEGMENT);
  }

  public static String getPropertyPath(String uri) {
    String decode = UriUtils.decode(uri, UriUtils.DEFAULT_ENCODING);

    String propertyPath = StringUtils.substringAfter(decode, baseUri);
    Preconditions.checkArgument(StringUtils.isNotBlank(propertyPath), "A property path uri must start with '" + baseUri + "'. Not a property path uri '" + uri + "'. ");

    return propertyPath;
  }
}
