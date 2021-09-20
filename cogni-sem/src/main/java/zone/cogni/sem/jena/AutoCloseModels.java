package zone.cogni.sem.jena;

import org.apache.jena.rdf.model.Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class AutoCloseModels implements AutoCloseable {

  private final Collection<Model> models = new ArrayList<>();

  public AutoCloseModels(Model... models) {
    this.models.addAll(Arrays.asList(models));
  }

  public Model add(Model model) {
    models.add(model);
    return model;
  }

  public AutoCloseModels addAll(Collection<Model> models) {
    this.models.addAll(models);
    return this;
  }

  @Override
  public void close() {
    JenaUtils.closeQuietly(models);
  }
}
