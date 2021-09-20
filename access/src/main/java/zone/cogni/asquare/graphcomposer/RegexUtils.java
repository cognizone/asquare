package zone.cogni.asquare.graphcomposer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class RegexUtils {

  @Deprecated
  // Reflectional access to named groups, should be replaced with capturing groups regex library
  public static Map<String, Integer> getNamedGroups(Pattern pattern) {
    try {
      Method method = Pattern.class.getDeclaredMethod("namedGroups");
      method.setAccessible(true);
      return (Map<String, Integer>)method.invoke(pattern);
    }
    catch (Exception ex) {
      return new HashMap<>();
    }
  }

}