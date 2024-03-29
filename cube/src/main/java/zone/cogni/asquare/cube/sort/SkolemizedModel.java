package zone.cogni.asquare.cube.sort;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

/**
 * The SkolemizedModel class implements a function that transforms a given RDF model by replacing blank nodes with SKOLEM URIs.
 * This class facilitates the skolemization process, where anonymous nodes are converted to globally unique SKOLEM URIs.
 */
public class SkolemizedModel implements Function<Model, Model> {

  private final String skolemBaseUri;


  public SkolemizedModel(@Nonnull String skolemBaseUri) {
    this.skolemBaseUri = skolemBaseUri;
  }

  @Override
  public Model apply(Model model) {
    Model result = ModelFactory.createDefaultModel();
    List<Statement> statements = new StatementSorter().apply(model);

    statements.forEach(statement -> {
      Resource newSubject = statement.getSubject().isURIResource() ? statement.getSubject()
                                                                   : getSkolemUri(statement.getSubject());

      RDFNode newObject = !statement.getObject().isAnon() ? statement.getObject()
                                                          : getSkolemUri(statement.getObject());

      Statement newStatement = ResourceFactory.createStatement(newSubject, statement.getPredicate(), newObject);
      result.add(newStatement);
    });

    return result;
  }

  private Resource getSkolemUri(RDFNode blankNode) {
    String fullSkolemPrefix = StringUtils.appendIfMissing(skolemBaseUri, "/") + ".well-known/genid/";
    return ResourceFactory.createResource(fullSkolemPrefix + blankNode.asResource().getId().getLabelString());
  }
}
