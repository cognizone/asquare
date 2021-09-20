package zone.cogni.asquare.service.queryapi.filter;

import org.apache.jena.rdf.model.Resource;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile.Attribute;
import zone.cogni.asquare.rdf.BasicRdfValue;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FilteredResource implements TypedResource {

  private final TypedResource inner;
  private final ResourceFilter filter;
  private final int depth;

  protected FilteredResource(TypedResource inner, ResourceFilter filter, int depth) {
    this.inner = inner;
    this.filter = filter;
    this.depth = depth;
  }

  public FilteredResource(TypedResource inner, ResourceFilter filter) {
    this.inner = inner;
    this.filter = filter;
    this.depth = 0;
  }


  @Nonnull
  @Override
  public ApplicationProfile.Type getType() {
    return inner.getType();
  }

  @Nonnull
  @Override
  public <T extends RdfValue> List<T> getValues(@Nonnull Attribute attribute) {
    if (shouldBeFiltered(attribute)) {
      return Collections.emptyList();
    }

    return filterValues(attribute, inner.getValues(attribute));
  }

  protected final boolean shouldBeFiltered(Attribute attribute) {
    return !filter.filterAttribute(depth, attribute);
  }

  protected final <T extends RdfValue> List<T> filterValues(Attribute attribute, List<T> values) {
    return filter.filterValues(depth, attribute, values)
        .map(v -> v instanceof BasicRdfValue ? v :  (T) wrap((TypedResource) v, filter, depth + 1))
        .collect(Collectors.toList());
  }

  protected final <T extends RdfValue> Optional<T> filterValue(Attribute attribute, T value) {
    return filterValues(attribute, Collections.singletonList(value)).stream().findAny();
  }

  protected FilteredResource wrap(TypedResource resource, ResourceFilter filter, int depth) {
    if (resource == null) return null;
    return new FilteredResource(resource, filter, depth);
  }

  @Nonnull
  @Override
  public Resource getResource() {
    return inner.getResource();
  }

  public TypedResource getUnfiltered() {
    return inner;
  }

}