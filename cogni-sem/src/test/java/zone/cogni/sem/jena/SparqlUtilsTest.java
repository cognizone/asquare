package zone.cogni.sem.jena;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import zone.cogni.libs.sparqlservice.impl.JenaModelSparqlService;

public class SparqlUtilsTest {
  private static final String URI = "https://fedlex.data.admin.ch/eli/oc/2023/423/legal-analysis";
  private static final String GRAPH_URI = URI + "/graph";
  private final JenaModelSparqlService jenaModelSparqlService = new JenaModelSparqlService();

  @Test
  public void test() {
    testEscape("A\ncomment"); // test newline
    testEscape("A\\comment"); // test backslash
    testEscape("A\bcomment"); // test backspace
    testEscape("I'mAComment"); // test single quote
    testEscape("A\fcomment"); // test form feed
    testEscape("A\tcomment");  // test tab
    testEscape("A\rcomment"); // test carriage return
    testEscape("com\"ment"); // test double quotes
  }

  private void testEscape(String value) {
    initializeSource(value);
    confirmResult(value);
  }

  private void initializeSource(String value) {
    jenaModelSparqlService.executeUpdateQuery(
      "PREFIX jolux: <http://data.legilux.public.lu/resource/ontology/jolux#> " +
      "INSERT DATA { " +
      "  GRAPH <" + GRAPH_URI + "> { " +
      "    <" + URI + "> a jolux:LegalAnalysis. " +
      "    <" + URI + "> jolux:impactFromLegalResourceComment \"" + SparqlUtils.escapeString(value) + "\"" +
      "  }" +
      "}"
    );
  }

  private void confirmResult(String value) {
    String query = "PREFIX jolux: <http://data.legilux.public.lu/resource/ontology/jolux#>\n" +
                   "SELECT ?comment\n" +
                   "WHERE {\n" +
                   "  GRAPH <" + GRAPH_URI + "> {\n" +
                   "    <" + URI + "> a jolux:LegalAnalysis ;\n" +
                   "                  jolux:impactFromLegalResourceComment ?comment .\n" +
                   "  }\n" +
                   "}";
    jenaModelSparqlService.executeSelectQuery(query, resultSet -> {
      resultSet.forEachRemaining(result -> Assertions.assertEquals(value, result.get("comment")
                                                                                .asLiteral()
                                                                                .getValue()));
      return null;
    });
    jenaModelSparqlService.dropGraph(GRAPH_URI);
  }
}
