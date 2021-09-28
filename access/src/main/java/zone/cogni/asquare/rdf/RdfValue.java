package zone.cogni.asquare.rdf;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import javax.annotation.Nonnull;

/**
 * Supertype of
 * - BasicRdfValue (a wrapper for Resource / Literal)
 * - TypedResource (a wrapper for Resource with types)
 */
public interface RdfValue {

  boolean isLiteral();

  @Nonnull
  default RDFNode getNode() {
    return isLiteral() ? getLiteral() : getResource();
  }

  @Nonnull
  Literal getLiteral();

  boolean isResource();

  @Nonnull
  Resource getResource();

  <T extends RdfValue> boolean isSameAs(T other);

}
