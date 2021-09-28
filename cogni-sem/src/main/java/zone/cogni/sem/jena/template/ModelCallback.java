package zone.cogni.sem.jena.template;


import org.apache.jena.rdf.model.Model;

@FunctionalInterface
public interface ModelCallback<T> {

  T doInModel(Model model);

}
