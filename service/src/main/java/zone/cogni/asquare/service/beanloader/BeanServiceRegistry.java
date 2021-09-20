package zone.cogni.asquare.service.beanloader;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.springframework.core.io.ClassPathResource;
import zone.cogni.asquare.rdf.TypedResource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.rules.RdfType;
import zone.cogni.sem.jena.JenaUtils;

import java.util.List;
import java.util.function.Function;

/**
 * Until we have a better idea of how to implement this we should not continue development.
 */
@Deprecated
public class BeanServiceRegistry {

  private final Model model;

  public BeanServiceRegistry() {
    model = JenaUtils.read(new ClassPathResource("application-profile/beans.ap.ttl"));
  }

  public String getServiceClass(TypedResource typedResource) {
    List<RDFNode> nodes = getServiceClassNodes(typedResource.getType());
    return asString().apply(nodes);
  }

  private List<RDFNode> getServiceClassNodes(ApplicationProfile.Type type) {
    NodeIterator nodeIterator = model.listObjectsOfProperty(
            ResourceFactory.createResource(type.getRule(RdfType.class).get().getValue()),
            ResourceFactory.createProperty("http://zone.cogni/asquare/model/serviceClass"));
    return nodeIterator.toList();
  }

  private Function<List<?>, String> asString() {
    return objects -> {
      Preconditions.checkNotNull(objects);
      Preconditions.checkState(objects.size() == 1);
      Object object = objects.get(0);

      Preconditions.checkState(object instanceof Literal);
      return ((Literal) object).getString();
    };
  }
}
