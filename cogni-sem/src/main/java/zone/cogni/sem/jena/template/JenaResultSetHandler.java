package zone.cogni.sem.jena.template;


import org.apache.jena.query.ResultSet;

public interface JenaResultSetHandler<T> {

  T handle(ResultSet resultSet);

}
