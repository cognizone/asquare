package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public abstract class RdfNode extends SingleValueRule<String> {

  RdfNode() {
  }

  RdfNode(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  public abstract RdfNode copy();

}
