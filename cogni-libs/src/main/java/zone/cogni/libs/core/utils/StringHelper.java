package zone.cogni.libs.core.utils;


import zone.cogni.libs.core.CognizoneException;

import java.io.UnsupportedEncodingException;

public class StringHelper {
  public static final String newline = "\n";
  public static final String tab = "\t";

  private StringHelper() {
  }

  public static byte[] toByteArray(String string) {
    return toByteArray(string, "UTF-8");
  }

  public static byte[] toByteArray(String string, String encoding) {
    try {
      return string.getBytes(encoding);
    }
    catch (UnsupportedEncodingException e) {
      throw CognizoneException.rethrow(e);
    }
  }
}
