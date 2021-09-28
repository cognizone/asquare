package zone.cogni.asquare.service.dataextraction;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.RdfType;

import java.util.List;
import java.util.function.Supplier;

public class TypedResourceModelBuilder implements Supplier<Model> {

  private static final Logger log = LoggerFactory.getLogger(TypedResourceModelBuilder.class);

  private Model model = ModelFactory.createDefaultModel();

  @Override
  public Model get() {
    return model;
  }

  public void addInstance(ApplicationProfile.Type type, TypedResource instance) {
    addInstance(type, instance, type.getAttributes().keySet());
  }

  public void addInstance(ApplicationProfile.Type type, TypedResource instance, Iterable<String> attributes) {
    addTypeFor(instance);
    attributes.forEach(attribute -> {
      Preconditions.checkState(type.hasAttribute(attribute));
      addAttribute(instance, type.getAttribute(attribute));
    });
  }

  private void addAttribute(TypedResource instance, ApplicationProfile.Attribute attribute) {
    List<RdfValue> values = instance.getValues(attribute);
    if (log.isTraceEnabled()) log.trace("attribute {} : {}", attribute.getAttributeId(), values.size());

    values.forEach(value -> {
      if (log.isTraceEnabled()) log.trace("attribute {} : {}", attribute.getAttributeId(), value);
      addValue(instance, attribute, value);
    });
  }

  private void addValue(TypedResource instance, ApplicationProfile.Attribute attribute, RdfValue value) {
    Property property = ResourceFactory.createProperty(attribute.getUri());

    if (value instanceof BasicRdfValue) {
      BasicRdfValue basicRdfValue = (BasicRdfValue) value;
      model.add(instance.getResource(), property, basicRdfValue.isLiteral() ? basicRdfValue.getLiteral()
                                                                            : basicRdfValue.getResource());
    }
    else if (value instanceof TypedResource) {
      TypedResource typedResource = (TypedResource) value;
      model.add(instance.getResource(), property, typedResource.getResource());
      addTypeFor(typedResource);
    }
    else {
      throw new RuntimeException("TODO missing type " + value.getClass().getCanonicalName());
    }
  }

  private void addTypeFor(TypedResource typedResource) {
    typedResource.getType()
            .getRule(RdfType.class)
            .map(rule -> rule.getValue())
            .forEach(uri -> {
              model.add(typedResource.getResource(), RDF.type, ResourceFactory.createResource(uri));
            });
  }
}
