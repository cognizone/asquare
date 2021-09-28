package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import org.apache.jena.rdf.model.Resource;

public class Reference extends RdfNode {

  public Reference() {
  }

  public Reference(Resource value) {
    this(value.getURI());
  }

  public Reference(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public Reference copy() {
    return new Reference(getValueCopy());
  }
}
