package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class ResourceReference extends SingleValueRule<String> {

  public ResourceReference() {
  }

  public ResourceReference(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public ResourceReference copy() {
    return new ResourceReference(getValueCopy());
  }

}
