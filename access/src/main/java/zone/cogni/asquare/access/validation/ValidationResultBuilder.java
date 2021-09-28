package zone.cogni.asquare.access.validation;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import zone.cogni.asquare.access.shacl.Shacl;
import zone.cogni.sem.jena.RdfStatements;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.apache.jena.rdf.model.ResourceFactory.createLangLiteral;

public class ValidationResultBuilder implements Supplier<RdfStatements> {

  public static Stream<Resource> filterRootValidationResults(List<Statement> details) {
    return details.stream()
            .filter(statement -> Objects.equals(statement.getPredicate(), RDF.type))
            .filter(statement -> Objects.equals(statement.getObject(), Shacl.ValidationResult))
            .filter(statement -> !(details.stream().filter(st -> Objects.equals(st.getPredicate(), Shacl.details))
                    .anyMatch(st -> Objects.equals(st.getObject(), statement.getSubject())))
            )
            .map(Statement::getSubject);
  }

  private final List<Statement> details = new ArrayList<>();
  private String message;
  private Resource focusNode;
  private Resource resultPath;
  private Resource sourceConstraintComponent;
  private Literal value;

  public ValidationResultBuilder withMessage(@Nonnull String message) {
    this.message = message;
    return this;
  }

  public ValidationResultBuilder withResultPath(@Nonnull String resultPath) {
    this.resultPath = ResourceFactory.createResource(resultPath);
    return this;
  }

  public ValidationResultBuilder withSourceConstraintComponent(@Nonnull Resource sourceConstraintComponent) {
    this.sourceConstraintComponent = sourceConstraintComponent;
    return this;
  }

  public ValidationResultBuilder withDetails(@Nonnull Collection<RdfStatements> statements) {
    statements.forEach(this::withDetails);
    return this;
  }

  public ValidationResultBuilder withDetails(@Nonnull RdfStatements statements) {
    details.addAll(statements.get());
    return this;
  }

  public ValidationResultBuilder withFocusNode(@Nonnull Resource focusNode) {
    this.focusNode = focusNode;
    return this;
  }

  public ValidationResultBuilder withValue(@Nonnull Literal value) {
    this.value = value;
    return this;
  }

  @Override
  public RdfStatements get() {
    RdfStatements statements = new RdfStatements();

    Resource result = ResourceFactory.createResource("http://shacl.org/report/result/" + UUID.randomUUID());
    statements.add(result, RDF.type, Shacl.ValidationResult);
    statements.add(result, Shacl.resultSeverity, Shacl.Severity.Violation);
    statements.add(result, Shacl.sourceConstraintComponent, sourceConstraintComponent);
    statements.add(result, Shacl.resultMessage, createLangLiteral(message, "en"));

    if (resultPath != null)
      statements.add(result, Shacl.resultPath, resultPath);
    if (focusNode != null)
      statements.add(result, Shacl.focusNode, focusNode);
    if (value != null)
      statements.add(result, Shacl.value, value);

    addDetails(statements, result);

    return statements;
  }

  private void addDetails(RdfStatements statements, Resource root) {
    filterRootValidationResults(details).forEach(nestedResult -> statements.add(root, Shacl.details, nestedResult));
    statements.add(details);
  }


}
