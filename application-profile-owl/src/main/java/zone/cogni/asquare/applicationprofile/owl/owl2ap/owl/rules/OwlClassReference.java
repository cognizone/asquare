package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import org.apache.jena.rdf.model.Resource;

public class OwlClassReference extends Reference {

  public OwlClassReference() {
  }

  public OwlClassReference(Resource value) {
    super(value);
  }

  public OwlClassReference(String value) {
    super(value);
  }

  @Override
  public OwlClassReference copy() {
    return new OwlClassReference(getValueCopy());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OwlClassReference)) return false;
    if (!super.equals(o)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
