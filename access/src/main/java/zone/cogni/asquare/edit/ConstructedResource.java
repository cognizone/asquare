package zone.cogni.asquare.edit;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConstructedResource implements MutableResource {

  public static Function<TypedResource, ConstructedResource> asConstructedResource() {
    return asConstructedResource(attributeId -> true);
  }

  public static Function<TypedResource, ConstructedResource> asConstructedResource(Predicate<String> attributeFilter) {
    return typedResource -> {
      if (typedResource instanceof ConstructedResource) return (ConstructedResource) typedResource;

      ConstructedResource result = new ConstructedResource(typedResource.getType(), typedResource.getResource().getURI());
      typedResource.getType()
              .getAttributes().values()
              .stream()
              .filter(attribute -> attributeFilter.test(attribute.getAttributeId()))
              .forEach(attribute -> {
                result.setValues(attribute, typedResource.getValues(attribute));
              });
      return result;
    };
  }

  public static Supplier<ConstructedResource> create(Supplier<ApplicationProfile.Type> typeSupplier, Supplier<String> uriSupplier) {
    return () -> new ConstructedResource(typeSupplier.get(), uriSupplier.get());
  }

  private final Map<ApplicationProfile.Attribute, List<? extends RdfValue>> valueMap = new HashMap<>();

  @Nonnull
  private final ApplicationProfile.Type type;

  @Nonnull
  private Resource resource;

  public ConstructedResource(@Nonnull ApplicationProfile.Type type,
                             @Nonnull String resource) {
    this.type = type;
    this.resource = ResourceFactory.createResource(resource);
  }

  @Nonnull
  @Override
  public ApplicationProfile.Type getType() {
    return type;
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  @Override
  public <T extends RdfValue> List<T> getValues(@Nonnull ApplicationProfile.Attribute attribute) {
    return Collections.unmodifiableList((List<T>) valueMap.getOrDefault(attribute, Collections.emptyList()));
  }

  @Nonnull
  @Override
  public Resource getResource() {
    return resource;
  }

  @Override
  public void setValues(@Nonnull ApplicationProfile.Attribute attribute, @Nonnull List<?> values) {
    valueMap.put(attribute, values.stream().map(val -> new AttributeConversion(attribute, val).get()).collect(Collectors.toList()));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void addValue(@Nonnull ApplicationProfile.Attribute attribute, @Nonnull Object value) {
    ((List<RdfValue>) valueMap
            .computeIfAbsent(attribute, (k) -> new ArrayList<>()))
            .add(new AttributeConversion(attribute, value).get());
  }

  @Override
  public void removeValue(@Nonnull ApplicationProfile.Attribute attribute,
                          @Nonnull Object value) {
    if (!valueMap.containsKey(attribute)) return;

    RdfValue toRemove = new AttributeConversion(attribute, value).get();

    List<? extends RdfValue> attributeValues = valueMap.get(attribute);
    attributeValues.stream()
            .filter(current -> Objects.equals(toRemove.getNode(), current.getNode()))
            .collect(Collectors.toList())
            .forEach(attributeValues::remove);
  }

  public void clearValues(@Nonnull ApplicationProfile.Attribute attribute) {
    valueMap.remove(attribute);
  }

  public boolean hasAttribute(@Nonnull ApplicationProfile.Attribute attribute) {
    return valueMap.containsKey(attribute);
  }

  public void setResource(@Nonnull String resource) {
    this.resource = ResourceFactory.createResource(resource);
  }

  public boolean isEmpty() {
    return valueMap.isEmpty();
  }
}
