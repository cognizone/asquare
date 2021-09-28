package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import org.apache.jena.rdf.model.Resource;

public class PropertyReference extends Reference {

  public PropertyReference() {
  }

  public PropertyReference(Resource value) {
    super(value);
  }

  public PropertyReference(String value) {
    super(value);
  }

  @Override
  public PropertyReference copy() {
    return new PropertyReference(getValueCopy());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PropertyReference)) return false;
    if (!super.equals(o)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
