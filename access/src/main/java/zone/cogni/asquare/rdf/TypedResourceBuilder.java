package zone.cogni.asquare.rdf;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Resource;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypedResourceBuilder {

  public static Supplier<ApplicationProfile.Type> type(ApplicationView view, String type) {
    Preconditions.checkState(view != null);
    Preconditions.checkState(type != null);

    return () -> view.getApplicationProfile().getType(type);
  }

  public static Supplier<ApplicationProfile.Type> type(ApplicationProfile.Type type) {
    Preconditions.checkState(type != null);

    return () -> type;
  }

  public static Supplier<String> uri(TypedResource typedResource, String attribute) {
    return uri(typedResource.getResource().getURI(), attribute);
  }

  public static Supplier<String> uri(String instanceUri, String attribute) {
    return uri(instanceUri + "/" + attribute + "/" + UUID.randomUUID());
  }

  public static Supplier<String> uri(Supplier<String>... parts) {
    return () -> Stream.of(parts)
            .map(Supplier::get)
            .collect(Collectors.joining("/"));
  }

  public static Supplier<String> part(TypedResource typedResource) {
    return part(typedResource.getResource().getURI());
  }

  public static Supplier<String> part(String string) {
    Preconditions.checkState(string != null);
    return () -> string;
  }

  public static Supplier<String> uri(Resource resource) {
    return uri(resource.getURI());
  }

  public static Supplier<String> uri(String uri) {
    Preconditions.checkState(uri != null);

    return () -> uri;
  }

}
