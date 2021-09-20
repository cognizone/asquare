package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import org.apache.jena.rdf.model.Resource;

public class ObjectPropertyReference extends PropertyReference {

  public ObjectPropertyReference() {
  }

  public ObjectPropertyReference(Resource value) {
    super(value);
  }

  public ObjectPropertyReference(String value) {
    super(value);
  }

  @Override
  public ObjectPropertyReference copy() {
    return new ObjectPropertyReference(getValueCopy());
  }

}
