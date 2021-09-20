package zone.cogni.asquare.edit.delta;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Delta {

  public static String convertStatementsToSparql(Collection<Statement> statements) {
    SparqlVisitor sparqlVisitor = SparqlVisitor.instance();
    return statements.stream()
            .map(statement -> "\n\t\t" +
                              statement.getSubject().visitWith(sparqlVisitor) +
                              " " +
                              statement.getPredicate().visitWith(sparqlVisitor) +
                              " " +
                              statement.getObject().visitWith(sparqlVisitor))
            .collect(Collectors.joining("."));
  }

  public abstract List<Statement> getAddStatements();

  public abstract List<Statement> getRemoveStatements();

  public Model getModel() {
    Preconditions.checkState(getRemoveStatements().isEmpty(), "Cannot get model. Deletes are present.");

    Model model = ModelFactory.createDefaultModel();
    model.add(getAddStatements());

    return model;
  }

  public String getSparql() {
    return Stream.of(getDeleteSparql(), getInsertSparql())
            .filter(Objects::nonNull)
            .collect(Collectors.joining(";\n"));
  }

  private String getDeleteSparql() {
    List<Statement> removeStatements = getRemoveStatements();
    if (removeStatements.isEmpty()) return null;

    return "DELETE DATA { \n" +
           convertStatementsToSparql(removeStatements) + "\n" +
           "} \n";
  }

  private String getInsertSparql() {
    List<Statement> addStatements = getAddStatements();
    if (addStatements.isEmpty()) return null;

    return "INSERT DATA { \n" +
           convertStatementsToSparql(addStatements) + "\n" +
           "} \n";
  }


}
