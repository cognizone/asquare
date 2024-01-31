package zone.cogni.libs.sparqlservice.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import zone.cogni.libs.core.utils.ApacheHttpClientUtils;

public class VirtuosoHelper {

  public static Model patchModel(Model model) {
    //avoid some weird virtuoso behaviour
    // it converts false to '0'^^xsd:integer
    List<Statement> booleanStatements = new ArrayList<>();
    model.listStatements().forEachRemaining(statement -> {
      RDFNode object = statement.getObject();
      if (!object.isLiteral() || !XSDDatatype.XSDboolean.getURI().equals(object.asLiteral().getDatatypeURI())) return;
      booleanStatements.add(statement);
    });

    model.remove(booleanStatements);
    booleanStatements.forEach(statement -> {
      Literal newObject = model.createTypedLiteral(statement.getLiteral().getBoolean() ? "1" : "0", XSDDatatype.XSDboolean);
      model.add(statement.getSubject(), statement.getPredicate(), newObject);
    });
    return model;
  }

  private static String getSparqlGraphProtocolGraphParam(final String graphUri) {
    return StringUtils.isBlank(graphUri) ? "default" : ("graph=" + graphUri);
  }

  private static String getVirtuosoUpdateUrl(final String sparqlEndpointUrl,
      final String graphIri) {
    return StringUtils.substringBeforeLast(sparqlEndpointUrl, "/") + "/sparql-graph-crud-auth?"
        + getSparqlGraphProtocolGraphParam(graphIri);
  }

  /**
   * Loads turtle data into a Virtuoso named graph.
   *
   * @param config Virtuoso server configuration
   * @param data turtle data
   * @param graphUri named graph IRI (must not be null)
   * @param put whether to put (replace) the data or post (update) them
   * @throws IOException in case an error occurs during HTTP connection.
   */
  public static void add(Config config, byte[] data, String graphUri, boolean put)
      throws IOException {
    final String url = getVirtuosoUpdateUrl(config.getUrl(), graphUri);
    ApacheHttpClientUtils.executeAuthenticatedPostOrPut(url, config.getUser(), config.getPassword(),
        config.isGraphCrudUseBasicAuth(), new ByteArrayEntity(data), put,
        "text/turtle;charset=utf-8");
  }
}
