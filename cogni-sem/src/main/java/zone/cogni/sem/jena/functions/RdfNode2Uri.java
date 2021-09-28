package zone.cogni.sem.jena.functions;

import org.apache.jena.rdf.model.RDFNode;

import java.util.function.Function;

public class RdfNode2Uri implements Function<RDFNode, String> {
  public static RdfNode2Uri function = new RdfNode2Uri();

  @Override
  public String apply(RDFNode input) {
    return input.isURIResource() ? input.asResource().getURI() : null;
  }
}
