package zone.cogni.asquare.rdf;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Resource;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RdfValueWrapper {

  public static RdfValueWrapper on(TypedResource instance) {
    return new RdfValueWrapper(instance);
  }

  public static Predicate<RdfValue> langFilter(String language) {
    return it -> it.isLiteral() && Objects.equals(it.getLiteral().getLanguage(), language);
  }

  public static Function<RdfValue, String> localName() {
    return rdfValue -> {
      if (rdfValue.isLiteral()) throw new RuntimeException("localName not allowed on literal");
      return rdfValue.getResource().getLocalName();
    };
  }

  public static Function<RdfValue, Resource> resource() {
    return rdfValue -> {
      if (rdfValue.isLiteral()) throw new IllegalStateException("Cannot convert literal to resource.");
      return rdfValue.getResource();
    };
  }

  public static Function<RdfValue, String> string() {
    return rdfValue -> rdfValue.isResource() ? rdfValue.getResource().getURI()
                                             : rdfValue.getLiteral().getString();
  }

  public static Function<RdfValue, Boolean> bool() {
    return rdfValue -> {
      if (rdfValue.isResource()) throw new IllegalStateException("Cannot convert resource " + rdfValue.getResource().getURI() + " to boolean.");

      return rdfValue.getLiteral().getBoolean();
    };
  }

  private final List<RdfValue> instances;

  public RdfValueWrapper(List<RdfValue> instances) {
    this.instances = instances;
  }

  public RdfValueWrapper(RdfValue instance) {
    this(Collections.singletonList(instance));
  }

  public RdfValueWrapper filter(Predicate<RdfValueWrapper> filter) {
    return new RdfValueWrapper(instances.stream()
                                        .map(RdfValueWrapper::new)
                                        .filter(filter)
                                        .flatMap(rdfValueWrapper -> rdfValueWrapper.instances.stream())
                                        .collect(Collectors.toList()));
  }

  public RdfValueWrapper get(String attribute) {
    return new RdfValueWrapper(instances.stream()
                                        .map(instance -> (TypedResource) instance)
                                        .flatMap(instance -> instance.getValues(attribute).stream())
                                        .collect(Collectors.toList())
    );
  }

  public RdfValueWrapper get(String attribute, Predicate<RdfValue> filter) {
    return new RdfValueWrapper(instances.stream()
                                        .map(instance -> (TypedResource) instance)
                                        .flatMap(instance -> instance.getValues(attribute).stream())
                                        .filter(filter)
                                        .collect(Collectors.toList())
    );
  }

  public <T> List<T> list(Function<RdfValue, T> conversion) {
    return instances.stream()
                    .map(conversion)
                    .collect(Collectors.toList());
  }

  public <T> T single(Function<RdfValue, T> conversion) {
    return single(conversion, null);
  }

  public <T> T single(Function<RdfValue, T> conversion, T alternative) {
    Iterator<T> iterator = instances.stream().map(conversion).collect(Collectors.toSet()).iterator();

    if (!iterator.hasNext()) return alternative;

    T result = iterator.next();
    Preconditions.checkState(!iterator.hasNext(), "Expected one result.");

    return result;
  }

  private <T> BinaryOperator<T> fail() {
    return (t, t2) -> {
      Preconditions.checkState(true, "Expected one result.");
      return null; // will never get here
    };
  }

}
