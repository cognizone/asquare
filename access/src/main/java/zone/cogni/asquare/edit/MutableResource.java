package zone.cogni.asquare.edit;

import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.rdf.TypedResource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface MutableResource extends TypedResource {

  void setValues(@Nonnull ApplicationProfile.Attribute attribute, @Nonnull List<?> values);

  default void setValues(@Nonnull String attributeId,
                        @Nonnull List<?> values) {
    setValues(getType().getAttribute(attributeId), values);
  }

  default void setValue(@Nonnull ApplicationProfile.Attribute attribute,
                       @Nullable Object value) {
    List<?> values = value == null ? new ArrayList<>() : Collections.singletonList(value);
    setValues(attribute, values);
  }

  default void setValue(@Nonnull String attributeId,
                       @Nullable Object value) {
    setValue(getType().getAttribute(attributeId), value);
  }

  void addValue(@Nonnull ApplicationProfile.Attribute attribute,
                                @Nonnull Object value);

  default void addValue(@Nonnull String attributeId,
                       @Nonnull Object value) {
    addValue(getType().getAttribute(attributeId), value);
  }

  void removeValue(@Nonnull ApplicationProfile.Attribute attribute,
                                   @Nonnull Object value);

  default void removeValue(@Nonnull String attributeId,
                          @Nonnull Object value) {
    removeValue(getType().getAttribute(attributeId), value);
  }

  boolean hasAttribute(ApplicationProfile.Attribute attribute);
  void clearValues(ApplicationProfile.Attribute attribute);
}
