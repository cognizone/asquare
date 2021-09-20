package zone.cogni.sem.jena;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class RdfStatements implements Supplier<List<Statement>> {

  public static Function<RdfStatements, Model> asModel() {
    return rdfStatements -> {
      Model model = ModelFactory.createDefaultModel();
      model.add(rdfStatements.statements);
      return model;
    };
  }

  private final List<Statement> statements = new ArrayList<>();

  public boolean isEmpty() {
    return statements.isEmpty();
  }

  public RdfStatements add(Resource s, Property p, List<? extends RDFNode> oList) {
    oList.forEach(o -> add(s, p, o));
    return this;
  }

  public RdfStatements add(Resource s, Property p, RDFNode o) {
    return add(ResourceFactory.createStatement(s, p, o));
  }

  public RdfStatements add(List<Statement> statements) {
    this.statements.addAll(statements);
    return this;
  }

  public RdfStatements add(Statement statement) {
    statements.add(statement);
    return this;
  }

  public RdfStatements add(Stream<RdfStatements> rdfStatementsStream) {
    rdfStatementsStream.forEach(this::add);
    return this;
  }

  public RdfStatements add(RdfStatements rdfStatements) {
    statements.addAll(rdfStatements.get());
    return this;
  }

  @Override
  public List<Statement> get() {
    return statements;
  }

  public String toString() {
    Model model = ModelFactory.createDefaultModel();
    model.setNsPrefix("shacl", "http://www.w3.org/ns/shacl#");
    model.add(statements);

    StringWriter writer = new StringWriter();
    writer.append("\n");

    model.write(writer, "ttl");
//    model.write(writer, "ntriple");
    return writer.toString();
  }
}
