package zone.cogni.sem.jena;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import zone.cogni.libs.sparqlservice.impl.JenaModelSparqlService;

public class SparqlUtilsTest {
  private JenaModelSparqlService jenaModelSparqlService;

  @Test
  public void test() {
    jenaModelSparqlService = new JenaModelSparqlService();
    initializeSource();
    confirmResult();
  }

  private void initializeSource() {
    jenaModelSparqlService.executeUpdateQuery(
      "PREFIX jolux: <http://data.legilux.public.lu/resource/ontology/jolux#> " +
      "INSERT DATA { " +
      "  GRAPH <https://fedlex.data.admin.ch/eli/oc/2023/423/legal-analysis/graph> { " +
      "    <https://fedlex.data.admin.ch/eli/oc/2023/423/legal-analysis> a jolux:LegalAnalysis. " +
      "    <https://fedlex.data.admin.ch/eli/oc/2023/423/legal-analysis> jolux:impactFromLegalResourceComment " + "'" + SparqlUtils.escapeLiteral("A\ncomment") + "'" +
      "  }" +
      "}"
    );
  }

  private void confirmResult() {
    String query = "PREFIX jolux: <http://data.legilux.public.lu/resource/ontology/jolux#>\n" +
                   "SELECT ?comment\n" +
                   "WHERE {\n" +
                   "  GRAPH <https://fedlex.data.admin.ch/eli/oc/2023/423/legal-analysis/graph> {\n" +
                   "    <https://fedlex.data.admin.ch/eli/oc/2023/423/legal-analysis> a jolux:LegalAnalysis ;\n" +
                   "                                                                  jolux:impactFromLegalResourceComment ?comment .\n" +
                   "  }\n" +
                   "}";
    jenaModelSparqlService.executeSelectQuery(query, resultSet -> {
      resultSet.forEachRemaining(result -> Assertions.assertEquals(result.get("comment")
                                                                         .toString(), "A\ncomment"));
      return null;
    });
  }
}
