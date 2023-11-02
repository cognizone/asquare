package zone.cogni.asquare.cube.sort;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The DeskolemizedModel class implements a function that transforms a given RDF model by replacing SKOLEM URIs with blank nodes.
 * This class is particularly useful in de-skolemization processes where SKOLEM URIs need to be converted back to anonymous blank nodes.
 */
public class DeskolemizedModel implements Function<Model, Model> {

  private final String skolemBaseUri;

  public DeskolemizedModel(String skolemBaseUri) {
    this.skolemBaseUri = skolemBaseUri;
  }

  @Override
  public Model apply(Model model) {
    Model result = ModelFactory.createDefaultModel();

    Map<String, Resource> skolemBlankNodeMap = new HashMap<>();
    model.listStatements().forEach(statement -> {
      Resource subject = statement.getSubject();
      if (isSkolemUri(subject)) {
        subject = ensureBlankNodeInMap(skolemBlankNodeMap, subject);
      }

      RDFNode object = statement.getObject();
      if (isSkolemUri(object)) {
        object = ensureBlankNodeInMap(skolemBlankNodeMap, object.asResource());
      }

      result.add(subject, statement.getPredicate(), object);
    });

    return result;
  }

  private Resource ensureBlankNodeInMap(Map<String, Resource> skolemBlankNodeMap, Resource skolem) {
    String skolemUri = skolem.getURI();
    Resource blankNode = skolemBlankNodeMap.get(skolemUri);
    if (blankNode == null) {
      blankNode = ModelFactory.createDefaultModel().createResource();
      skolemBlankNodeMap.put(skolemUri, blankNode);
    }

    return blankNode;
  }

  private boolean isSkolemUri(RDFNode rdfNode) {
    if (!rdfNode.isURIResource()) return false;

    String fullSkolemPrefix = StringUtils.appendIfMissing(skolemBaseUri, "/") + ".well-known/genid/";
    return rdfNode.asResource().getURI().startsWith(fullSkolemPrefix);
  }
}
