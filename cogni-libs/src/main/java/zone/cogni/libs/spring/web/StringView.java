package zone.cogni.libs.spring.web;

import org.springframework.http.HttpStatus;
import zone.cogni.libs.core.utils.StringHelper;

public class StringView extends DataView {

  public StringView(String value) {
    super(StringHelper.toByteArray(value), "text/plain", "UTF-8");
  }

  public StringView(HttpStatus httpStatus) {
    super(StringHelper.toByteArray(httpStatus.getReasonPhrase()), "text/plain", "UTF-8", httpStatus);
  }

  public StringView(String value, HttpStatus httpStatus) {
    super(StringHelper.toByteArray(value), "text/plain", "UTF-8", httpStatus);
  }


}
