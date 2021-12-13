package zone.cogni.asquare.edit.delta;

import org.apache.jena.rdf.model.Statement;

import java.util.Collections;
import java.util.List;

public class DeltaFake extends Delta {

  private final List<Statement> addStatements;
  private final List<Statement> removeStatements;

  public DeltaFake(List<Statement> addStatements, List<Statement> removeStatements) {
    this.addStatements = addStatements;
    this.removeStatements = removeStatements;
  }

  @Override
  public List<Statement> getAddStatements() {
    return addStatements == null ? Collections.emptyList() : addStatements;
  }

  @Override
  public List<Statement> getRemoveStatements() {
    return removeStatements == null ? Collections.emptyList() : removeStatements;
  }
}
