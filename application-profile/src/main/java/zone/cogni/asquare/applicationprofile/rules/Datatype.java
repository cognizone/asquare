package zone.cogni.asquare.applicationprofile.rules;

import com.google.common.base.Preconditions;
import zone.cogni.asquare.applicationprofile.Constants;
import zone.cogni.asquare.applicationprofile.model.SingleValueRule;

public class Datatype extends SingleValueRule<String> {

  public static Datatype datatype(String datatype) {
    Preconditions.checkArgument(Constants.datatypes.contains(datatype));
    return new Datatype(datatype);
  }

  public Datatype() {
  }

  public Datatype(String value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public Datatype copy() {
    return new Datatype(getValueCopy());
  }
}
