package zone.cogni.asquare.edit.delta;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Delta {

  // only changeable for tests; should normally not change
  public static int PARTITION_SIZE = 1000;

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
    return Stream.concat(getDeleteSparql(), getInsertSparql())
                 .collect(Collectors.joining(";\n"));
  }

  private Stream<String> getDeleteSparql() {
    List<Statement> removeStatements = getRemoveStatements();
    if (removeStatements.isEmpty()) return Stream.empty();

    return Lists.partition(removeStatements, PARTITION_SIZE)
                .stream()
                .map(statements -> "DELETE DATA { \n" +
                                   convertStatementsToSparql(statements) + "\n" +
                                   "} \n");
  }

  private Stream<String> getInsertSparql() {
    List<Statement> addStatements = getAddStatements();
    if (addStatements.isEmpty()) return Stream.empty();

    return Lists.partition(addStatements, PARTITION_SIZE)
                .stream()
                .map(statements -> "INSERT DATA { \n" +
                                   convertStatementsToSparql(statements) + "\n" +
                                   "} \n");
  }

}
