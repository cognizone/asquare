package zone.cogni.asquare.access.util;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RdfParserUtilsTest {

  private final Model model = ModelFactory.createDefaultModel();

  @Test
  public void parsePlainLiteral() {
    Literal literal = RdfParserUtils.parseLiteral("\"Fred\"");
    assertEquals("Fred", literal.getString());
  }

  @Test
  public void parseTypedLiteral() {
    Literal literal = RdfParserUtils.parseLiteral("\"42\"^^<http://www.w3.org/2001/XMLSchema#integer>");
    assertEquals(42, literal.getInt());
  }

  @Test
  public void parseLangLiteral() {
    Literal literal = RdfParserUtils.parseLiteral("\"Car\"@en");
    assertEquals("Car", literal.getString());
    assertEquals("en", literal.getLanguage());
  }

  @Test
  public void parseLiteralWithModel() {
    Literal literal = RdfParserUtils.parseLiteral(model, "\"Annie\"");
    assertEquals("Annie", literal.getString());
  }

  @Test
  public void parseResource() {
    Resource resource = RdfParserUtils.parseResource(model, "<http://demo.com/data/person/1>");
    assertEquals("http://demo.com/data/person/1", resource.getURI());
  }

  @Test
  public void parseRdfNodeUri() {
    RDFNode node = RdfParserUtils.parseRdfNode(model, "<http://demo.com/data/person/2>");
    assertTrue(node.isResource());
    assertEquals("http://demo.com/data/person/2", node.asResource().getURI());
  }

  @Test
  public void parseRdfNodeLiteral() {
    RDFNode node = RdfParserUtils.parseRdfNode("\"hello\"");
    assertTrue(node.isLiteral());
    assertEquals("hello", node.asLiteral().getString());
  }

  @Test
  public void parseInvalidTtlThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> RdfParserUtils.parseLiteral("not valid ttl @@##"));
  }
}
