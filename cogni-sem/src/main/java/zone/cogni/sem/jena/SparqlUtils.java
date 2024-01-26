package zone.cogni.sem.jena;

import org.apache.commons.lang3.StringUtils;

public enum SparqlUtils {
  ;

  public static String escapeLiteral(String literal) {
    return StringUtils.replace(literal, "'", "\\'")
                      .replace("\n", "\\n");

  }
}
