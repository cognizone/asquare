package zone.cogni.asquare.applicationprofile.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

public interface Rule<T extends Rule<T>> {

  T copy();

  @JsonIgnore
  default String getType() {
    return getClass().getSimpleName();
  }

  @JsonProperty("name")
  default String getRuleName() {
    return StringUtils.uncapitalize(getType());
  }

}
