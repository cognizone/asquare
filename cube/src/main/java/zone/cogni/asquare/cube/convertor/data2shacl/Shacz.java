package zone.cogni.asquare.cube.convertor.data2shacl;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public interface Shacz {
  String NS = "https://data.cogni.zone/model/ui/shacl-extension/";

  Resource ShapesGraph = ResourceFactory.createResource(NS + "ShapesGraph");
  Property shapes = ResourceFactory.createProperty(NS + "shapes");

}
