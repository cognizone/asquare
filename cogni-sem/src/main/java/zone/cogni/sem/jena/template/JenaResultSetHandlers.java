package zone.cogni.sem.jena.template;


import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.RDFNode;
import zone.cogni.sem.jena.JenaUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public abstract class JenaResultSetHandlers {

  public static final JenaResultSetHandler<List<Map<String, RDFNode>>> listOfMapsResolver = JenaQueryUtils::convertToListOfMaps;

  public static final JenaResultSetHandler<List<String>> listOfStrings = new ListResultSetHandler<String>() {
    @Override
    protected String handleRow(QuerySolution querySolution) {
      RDFNode rdfNode = querySolution.get(querySolution.varNames().next());
      return null == rdfNode ? null : JenaUtils.stringize(rdfNode);
    }
  };

  public static final JenaResultSetHandler<List<RDFNode>> listOfRdfNodes = new ListResultSetHandler<RDFNode>() {
    @Override
    protected RDFNode handleRow(QuerySolution querySolution) {
      return querySolution.get(querySolution.varNames().next());
    }
  };

  public static final JenaResultSetHandler<Optional<RDFNode>> firstResultOptional = resultSet ->
    resultSet.hasNext() ? Optional.ofNullable(resultSet.next().get(resultSet.getResultVars().get(0))) : Optional.empty();



  public static <T> JenaResultSetHandler<List<T>> listResultSetHandler(Function<QuerySolution, T> mapper) {
    return new ListResultSetHandler<T>() {
      @Override
      protected T handleRow(QuerySolution querySolution) {
        return mapper.apply(querySolution);
      }
    };
  }
}
