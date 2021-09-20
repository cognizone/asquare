package zone.cogni.asquare.service.queryapi.filter;

import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.AttributeConversion;
import zone.cogni.asquare.edit.MutableResource;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

public class MutableFilteredResource extends FilteredResource implements MutableResource {

  private final MutableResource innerMutable;
  public MutableFilteredResource(MutableResource inner, ResourceFilter filter) {
    super(inner, filter);
    this.innerMutable = inner;
  }

  public MutableFilteredResource(MutableResource inner, ResourceFilter filter, int depth) {
    super(inner, filter, depth);
    this.innerMutable = inner;
  }


  @Override
  public void setValues(@Nonnull ApplicationProfile.Attribute attribute, @Nonnull List<?> values) {
    if (shouldBeFiltered(attribute)) return;

    List<RdfValue> asRdfValues = values.stream()
        .map(val -> new AttributeConversion(attribute, val).get())
        .collect(Collectors.toList());
    List<RdfValue> filtered = filterValues(attribute, asRdfValues);

    innerMutable.setValues(attribute, filtered);
  }

  @Override
  public void addValue(@Nonnull ApplicationProfile.Attribute attribute, @Nonnull Object value) {
    if (shouldBeFiltered(attribute)) return;

    //todo should be able to filter based on all values of attribute (eg. for LatestDatePipe)
    RdfValue asRdfValue = new AttributeConversion(attribute, value).get();
    filterValue(attribute, asRdfValue).ifPresent(v -> innerMutable.addValue(attribute, v));
  }

  @Override
  protected MutableFilteredResource wrap(TypedResource resource, ResourceFilter filter, int depth) {
    if (resource == null) return null;
    return new MutableFilteredResource((MutableResource) resource, filter, depth);
  }

  @Override
  public void removeValue(@Nonnull ApplicationProfile.Attribute attribute, @Nonnull Object value) {
    innerMutable.removeValue(attribute, value);
  }

  @Override
  public boolean hasAttribute(ApplicationProfile.Attribute attribute) {
    return innerMutable.hasAttribute(attribute);
  }

  @Override
  public void clearValues(ApplicationProfile.Attribute attribute) {
    innerMutable.clearValues(attribute);
  }

  @Override
  public MutableResource getUnfiltered() {
    return innerMutable;
  }
}
