package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import org.apache.jena.rdf.model.Resource;

public class PropertyReferenceCandidate extends PropertyReference {

  public PropertyReferenceCandidate() {
  }

  public PropertyReferenceCandidate(Resource value) {
    super(value);
  }

  public PropertyReferenceCandidate(String value) {
    super(value);
  }

  @Override
  public PropertyReferenceCandidate copy() {
    return new PropertyReferenceCandidate(getValueCopy());
  }

}
