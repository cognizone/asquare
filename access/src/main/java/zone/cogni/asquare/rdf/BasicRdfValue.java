package zone.cogni.asquare.rdf;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;

import javax.annotation.Nonnull;
import java.util.Objects;

public class BasicRdfValue implements RdfValue {

  private final Literal literal;
  private final Resource resource;

  public BasicRdfValue(Literal literal) {
    this.literal = literal;
    resource = null;
  }

  public BasicRdfValue(Resource resource) {
    literal = null;
    this.resource = resource;
  }

  @Override
  public boolean isLiteral() {
    return literal != null;
  }

  @Nonnull
  @Override
  public Literal getLiteral() {
    if (literal != null) return literal;
    throw new IllegalStateException("Cannot call getLiteral since it is not a Literal (" + resource + ")");
  }

  @Override
  public boolean isResource() {
    return resource != null;
  }

  @Nonnull
  @Override
  public Resource getResource() {
    if (resource != null) return resource;
    throw new IllegalStateException("Cannot call getResource since it is not a Resource (" + literal + ")");
  }

  @Override
  public <T extends RdfValue> boolean isSameAs(T other) {
    if (this == other) return true;
    if (!(other instanceof BasicRdfValue)) return false;

    BasicRdfValue that = (BasicRdfValue) other;
    return isLiteral() ? Objects.equals(literal, that.literal)
                       : Objects.equals(resource, that.resource);
  }

  @Override
  public String toString() {
    String substring = literal != null ? literal.getLexicalForm() : resource.getURI();
    return "BasicRdfValue (" + substring + ")";
  }
}
