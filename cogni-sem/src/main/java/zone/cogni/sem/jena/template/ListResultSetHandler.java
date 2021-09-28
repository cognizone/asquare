package zone.cogni.sem.jena.template;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import java.util.ArrayList;
import java.util.List;

public abstract class ListResultSetHandler<T> implements JenaResultSetHandler<List<T>> {

  @Override
  public List<T> handle(ResultSet resultSet) {
    List<T> result = new ArrayList<>();
    while (resultSet.hasNext()) {
      result.add(handleRow(resultSet.nextSolution()));
    }
    return result;
  }

  protected abstract T handleRow(QuerySolution querySolution);
}
