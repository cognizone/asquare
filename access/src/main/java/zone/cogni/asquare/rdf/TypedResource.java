package zone.cogni.asquare.rdf;

import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Literal;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Abstract base class for resources that are instances of one or more classes defined in the application profile.
 * It abstracts from the actual type of underlying data source, such as a rdf store or elastic core.
 * <p>
 * This class represents a resource and defines an abstract interface to access the resource and its attributes
 * as defined in the application profile.
 * <p>
 * Main methods are "getApplicationProfile"; "getResource()"; "getTypes"; and "getValues";
 */
public interface TypedResource extends RdfValue {

  @Override
  default boolean isLiteral() {
    return false;
  }

  @Nonnull
  @Override
  default Literal getLiteral() {
    throw new IllegalStateException("Cannot call getLiteral on a TypedResource");
  }

  @Override
  default boolean isResource() {
    return true;
  }

  @Override
  default  <T extends RdfValue> boolean isSameAs(T other) {
    if (this == other) return true;
    if (!(other instanceof TypedResource)) return false;

    TypedResource that = (TypedResource) other;
    return Objects.equals(getType(), that.getType())
           && Objects.equals(getResource(), that.getResource());
  }

  @Nonnull
  ApplicationProfile.Type getType();

  @Nonnull
  default ApplicationProfile getApplicationProfile() {
    return getType().getApplicationProfile();
  }

  default boolean hasValues(@Nonnull ApplicationProfile.Attribute attribute) {
    return !getValues(attribute).isEmpty();
  }

  default boolean hasValues(@Nonnull String attributeId) {
    return hasValues(getType().getAttribute(attributeId));
  }

  @Nonnull
  <T extends RdfValue> List<T> getValues(@Nonnull ApplicationProfile.Attribute attribute);

  @Nonnull
  default  <T extends RdfValue> List<T> getValues(@Nonnull String attributeId) {
    ApplicationProfile.Attribute attribute = getType().getAttribute(attributeId);
    return getValues(attribute);
  }

  @Nullable
  default  <T extends RdfValue> T getValue(@Nonnull ApplicationProfile.Attribute attribute) {
    List<T> values = getValues(attribute);

    Preconditions.checkState(values.size() <= 1);
    return values.isEmpty() ? null : values.get(0);
  }

  @Nullable
  default  <T extends RdfValue> T getValue(@Nonnull String attributeId) {
    ApplicationProfile.Attribute attribute = getType().getAttribute(attributeId);
    return getValue(attribute);
  }


}
