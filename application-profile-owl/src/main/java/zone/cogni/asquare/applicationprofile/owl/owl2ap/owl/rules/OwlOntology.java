package zone.cogni.asquare.applicationprofile.owl.owl2ap.owl.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class OwlOntology extends SingleValueRule<Reference> {

  public OwlOntology() {
  }

  public OwlOntology(Reference value) {
    super(value);
  }

  @Override
  public Class<Reference> allowedType() {
    return Reference.class;
  }

  @Override
  public OwlOntology copy() {
    return new OwlOntology(getValueCopy());
  }

}
