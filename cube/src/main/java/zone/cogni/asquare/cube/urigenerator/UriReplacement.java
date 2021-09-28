package zone.cogni.asquare.cube.urigenerator;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

import java.util.List;

public class UriReplacement {

  public static void replace(Model model, String original, String replacement) {
    Resource originalResource = ResourceFactory.createResource(original);
    Resource replacementResource = ResourceFactory.createResource(replacement);

    List<Statement> leftStatements = model.listStatements(originalResource, null, (RDFNode) null).toList();
    model.remove(leftStatements);

    leftStatements.forEach(statement -> {
      model.add(replacementResource, statement.getPredicate(), statement.getObject());
    });


    List<Statement> rightStatements = model.listStatements(null, null, originalResource).toList();
    model.remove(rightStatements);

    rightStatements.forEach(statement -> {
      model.add(statement.getSubject(), statement.getPredicate(), replacementResource);
    });
  }

}
