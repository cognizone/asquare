package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import org.apache.jena.rdf.model.Resource;

public class OwlClassReferenceCandidate extends Reference {

  public OwlClassReferenceCandidate() {
  }

  public OwlClassReferenceCandidate(Resource value) {
    super(value);
  }

  public OwlClassReferenceCandidate(String value) {
    super(value);
  }

  @Override
  public OwlClassReferenceCandidate copy() {
    return new OwlClassReferenceCandidate(getValueCopy());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OwlClassReferenceCandidate)) return false;
    if (!super.equals(o)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
