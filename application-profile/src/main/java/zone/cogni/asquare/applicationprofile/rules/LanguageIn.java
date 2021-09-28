package zone.cogni.asquare.applicationprofile.rules;

import zone.cogni.asquare.applicationprofile.model.ListSingleValueRule;

import java.util.List;

public class LanguageIn extends ListSingleValueRule<String> {

  public LanguageIn() {
  }

  public LanguageIn(List<String> value) {
    super(value);
  }

  @Override
  public Class<String> allowedType() {
    return String.class;
  }

  @Override
  public LanguageIn copy() {
    return new LanguageIn(getValueCopy());
  }
}
