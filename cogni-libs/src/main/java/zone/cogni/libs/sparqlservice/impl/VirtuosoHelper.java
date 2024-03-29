package zone.cogni.libs.sparqlservice.impl;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;

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

  public static String getVirtuosoUpdateUrl(final String sparqlEndpointUrl,
      final String graphIri) {
    return getVirtuosoGspFromSparql(sparqlEndpointUrl) + "?" + (StringUtils.isBlank(graphIri)
        ? "default" : ("graph=" + graphIri));
  }

  public static String getVirtuosoGspFromSparql(final String sparqlEndpointUrl) {
    return StringUtils.substringBeforeLast(sparqlEndpointUrl, "/") + "/sparql-graph-crud-auth";
  }
}
