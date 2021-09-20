package zone.cogni.asquare.cube.convertor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.rdf.model.Model;

import java.util.function.BiFunction;

public class ModelCleanup implements BiFunction<Model, String, Model> {

  private final ModelToJsonConversion modelToJsonConversion;
  private final JsonToModelConversion jsonToModelConversion;

  public ModelCleanup(ModelToJsonConversion modelToJsonConversion,
                      JsonToModelConversion jsonToModelConversion) {
    this.modelToJsonConversion = modelToJsonConversion;
    this.jsonToModelConversion = jsonToModelConversion;
  }

  @Override
  public Model apply(Model model, String root) {
    ObjectNode apply = modelToJsonConversion.apply(model, root);
    return jsonToModelConversion.apply(apply);
  }
}
