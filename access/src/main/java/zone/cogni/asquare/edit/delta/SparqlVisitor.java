package zone.cogni.asquare.edit.delta;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.Resource;

public class SparqlVisitor implements RDFVisitor {

  public static SparqlVisitor instance() {
    return new SparqlVisitor();
  }

  @Override
  public String visitBlank(Resource r, AnonId id) {
    throw new UnsupportedOperationException("Cannot convert blanks into SPARQL.");
  }

  @Override
  public String visitURI(Resource r, String uri) {
    return "<" + r.getURI() + ">";
  }

  @SuppressWarnings("ConditionalExpressionWithNegatedCondition")
  @Override
  public String visitLiteral(Literal literal) {
    String typeInformation = StringUtils.isNotBlank(literal.getLanguage())
                             ? "@" + literal.getLanguage()
                             : "^^<" + literal.getDatatypeURI() + ">";

    return "'''" + echarEncode(literal.getLexicalForm()) + "'''" + typeInformation;
  }

  /**
   * Based on https://www.w3.org/TR/sparql11-query/#rECHAR
   * but only escaping ' and \
   */
  @SuppressWarnings({"SwitchStatement", "HardcodedLineSeparator", "fallthrough"})
  private String echarEncode(String input) {
    StringBuilder result = new StringBuilder(input.length() + 10);
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      switch (c) {
        case '\\' :
//        case '\"' :
        case '\'' :
//        case '\n' :
//        case '\t' :
//        case '\b' :
//        case '\r' :
//        case '\f' :
          result.append('\\');
        default:
          result.append(c);
      }
    }
    return result.toString();
  }

}
