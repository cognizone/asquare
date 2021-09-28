package zone.cogni.sem.jena.template;

public interface JenaOperations {

  <T> T execute(ModelCallback<T> modelOperations);

}
