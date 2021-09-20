package zone.cogni.asquare.access.validation;

import zone.cogni.sem.jena.RdfStatements;

public abstract class ValidationResult {

  public abstract boolean conforms();

  public abstract RdfStatements getRdfStatements();

  public abstract void add(RdfStatements rdfStatements);

  public static class Conforming extends ValidationResult {

    private boolean conforms = true;

    @Override
    public boolean conforms() {
      return conforms;
    }

    @Override
    public RdfStatements getRdfStatements() {
      throw new IllegalStateException("getRdfStatements for ConformingValidationResult");
    }

    @Override
    public void add(RdfStatements rdfStatements) {
      if (rdfStatements.isEmpty()) return;

      conforms = false;
      // no-op
    }

  }

  public static class Reporting extends ValidationResult {

    private boolean conforms = true;
    private final RdfStatements rdfStatements = new RdfStatements();

    @Override
    public boolean conforms() {
      return conforms;
    }

    @Override
    public RdfStatements getRdfStatements() {
      return rdfStatements;
    }

    @Override
    public void add(RdfStatements rdfStatements) {
      if (rdfStatements.isEmpty()) return;

      conforms = false;
      this.rdfStatements.add(rdfStatements);
    }

  }

}
