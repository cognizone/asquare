package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import org.apache.jena.rdf.model.Resource;

public class DatatypePropertyReference extends PropertyReference {

  public DatatypePropertyReference() {
  }

  public DatatypePropertyReference(Resource value) {
    super(value);
  }

  public DatatypePropertyReference(String value) {
    super(value);
  }

  @Override
  public DatatypePropertyReference copy() {
    return new DatatypePropertyReference(getValueCopy());
  }

}
