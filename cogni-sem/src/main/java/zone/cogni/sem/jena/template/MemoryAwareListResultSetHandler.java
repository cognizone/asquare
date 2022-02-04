package zone.cogni.sem.jena.template;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public abstract class MemoryAwareListResultSetHandler<T> extends ListResultSetHandler<T> {

  @Override
  public List<T> handle(final ResultSet resultSet) {
    final SoftReference<List<T>> resultRef = new SoftReference<>(new LinkedList<>());

    while (resultSet.hasNext()) {
      final T row = handleRow(resultSet.next());
      Optional.ofNullable(resultRef.get())
        .orElseThrow(() -> new TooManyResultsException(
            "The result is too big or memory is overused, number of processed rows: " + resultSet.getRowNumber()
        )).add(row);
    }

    return Optional.ofNullable(resultRef.get()).map(ArrayList::new).orElseThrow(TooManyResultsException::new);
  }

  protected abstract T handleRow(QuerySolution querySolution);
}
