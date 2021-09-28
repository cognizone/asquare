package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public abstract class Restriction extends SingleValueRule<Reference> {

  protected Restriction() {
  }

  protected Restriction(Reference value) {
    super(value);
  }

  @Override
  public Class<Reference> allowedType() {
    return Reference.class;
  }

}
