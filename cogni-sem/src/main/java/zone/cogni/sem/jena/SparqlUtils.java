package zone.cogni.sem.jena;

public enum SparqlUtils {
  ;

  public static String escapeString(String literal) {
    return literal.replace("\\", "\\\\") // Escape backslash, needs to be first otherwise it will escape the backslashes of the already escaped literals
                  .replace("\t", "\\t")  // Escape tab
                  .replace("\b", "\\b")  // Escape backspace
                  .replace("\n", "\\n")  // Escape newline
                  .replace("\r", "\\r")  // Escape carriage return
                  .replace("\f", "\\f")  // Escape formfeed
                  .replace("'", "\\'")   // Escape single quote
                  .replace("\"", "\\\""); // Escape double quote
  }
}
