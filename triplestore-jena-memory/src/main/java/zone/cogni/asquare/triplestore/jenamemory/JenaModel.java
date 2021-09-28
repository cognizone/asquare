package zone.cogni.asquare.triplestore.jenamemory;

import org.apache.jena.rdf.model.Model;
import org.springframework.core.io.Resource;
import zone.cogni.core.util.function.CachingSupplier;
import zone.cogni.sem.jena.JenaUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class JenaModel implements Supplier<Model> {

  private final Object lock = new Object();
  private Supplier<Model> modelSupplier;

  private List<Resource> resources;

  public List<Resource> getResources() {
    return Collections.unmodifiableList(resources);
  }

  public void setResources(List<Resource> resources) {
    this.resources = new ArrayList<>(resources);
  }

  @Override
  public Model get() {
    synchronized (lock) {
      if (modelSupplier == null) {
        modelSupplier = CachingSupplier.memoize(() -> JenaUtils.read(resources));
      }

      return modelSupplier.get();
    }
  }
}
