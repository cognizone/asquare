package zone.cogni.asquare.elasticproxy;


import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ParamValidator {
  private Set<String> values;
  private Set<Pattern> patterns;
  private String value;
  private Pattern pattern;

  public Set<String> getValues() {
    return values;
  }

  public void setValues(Set<String> values) {
    this.values = values;
  }

  public Set<Pattern> getPatterns() {
    return patterns;
  }

  public void setPatterns(Set<String> patterns) {
    this.patterns = patterns.stream().map(Pattern::compile).collect(Collectors.toSet());
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Pattern getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = Pattern.compile(pattern);
  }

  public boolean validate(Object value) {
    if (value != null) {
      if (value instanceof String[]) {
        for (String item : (String[]) value) {
          if (!validate(item)) {
            return false;
          }
        }
      }
      else {
        String paramValue = value.toString();
        if (!validate(paramValue)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean validate(String value) {
    if (values != null && !values.contains(value)) {
      return false;
    }
    if (this.value != null && !this.value.equals(value)) {
      return false;
    }
    if (pattern != null && !pattern.matcher(value).matches()) {
      return false;
    }
    if (patterns != null) {
      for (Pattern pattern : patterns) {
        if (!pattern.matcher(value).matches()) {
          return false;
        }
      }
    }
    return true;
  }
}
