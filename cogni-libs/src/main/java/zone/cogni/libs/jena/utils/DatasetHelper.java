package zone.cogni.libs.jena.utils;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;

public class DatasetHelper {
  private DatasetHelper() {
  }

  /**
   * <p>
   * Create and return an in memory dataset copy.
   * </p>
   * <p>
   * The operation runs in a write transaction.
   * </p>
   * <p>
   * It should be noted that this might have a big performance or memory impact when huge datasets are copied.
   * </p>
   *
   * @return a copy of current Dataset
   */
  public static Dataset copy(Dataset dataset) {
    Dataset datasetCopy = DatasetFactory.create();

    try {
      dataset.getLock().enterCriticalSection(false);

      // copy named models
      dataset.listNames()
             .forEachRemaining(name -> {
               Model datasetNamedModel = dataset.getNamedModel(name);
               datasetCopy.getNamedModel(name)
                          .add(datasetNamedModel);
             });

      // copy default model
      Model datasetDefaultModel = dataset.getDefaultModel();
      datasetCopy.getDefaultModel().add(datasetDefaultModel);
    }
    finally {
      dataset.getLock().leaveCriticalSection();
    }

    return datasetCopy;
  }
}
