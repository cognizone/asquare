package zone.cogni.sem.jena.template;

import org.apache.jena.rdf.model.RDFNode;

import java.util.List;
import java.util.Map;

public interface ListOfMapHandler<T> {

  T handle(List<Map<String, RDFNode>> resultSet);

}
