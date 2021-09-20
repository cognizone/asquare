package zone.cogni.sem.jena.model;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResultSetDto {

  private List<String> vars;
  private List<QuerySolutionDto> querySolutions;

  public List<String> getVars() {
    return vars;
  }

  public void setVars(List<String> vars) {
    this.vars = vars;
  }

  public List<QuerySolutionDto> getQuerySolutions() {
    return querySolutions;
  }

  public void setQuerySolutions(List<QuerySolutionDto> querySolutions) {
    this.querySolutions = querySolutions;
  }

  public Stream<QuerySolutionDto> stream() {
    return querySolutions == null ? Stream.empty() : querySolutions.stream();
  }

  public List<String> collectPropertyValues(String propertyName) {
    return stream().map(q -> q.getProperty(propertyName)).collect(Collectors.toList());
  }

  public Map<String, QuerySolutionDto> mapProperty(String propertyName) {
    return stream().collect(Collectors.toMap(q -> q.getProperty(propertyName), q -> q));
  }

  public String collectPropertyValue(String propertyName) {
    if(getQuerySolutions() != null && getQuerySolutions().size() == 1) {
      QuerySolutionDto dto = getQuerySolutions().get(0);
      return dto.getProperty(propertyName);
    }
    return null;
  }

  @Override
  public String toString() {
    StringBuilder bld = new StringBuilder();
    for (String name : vars) {
      bld.append(name).append("\t");
    }
    bld.append(StringUtils.LF);
    for (QuerySolutionDto qs : querySolutions) {
      for (String name : vars) {
        bld.append(qs.getProperty(name)).append("\t");
      }
      bld.append(StringUtils.LF);
    }
    return bld.toString();
  }
}
