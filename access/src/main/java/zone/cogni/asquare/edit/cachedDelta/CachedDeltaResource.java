package zone.cogni.asquare.edit.cachedDelta;

import zone.cogni.asquare.access.AccessType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CachedDeltaResource {
  AccessType accessType() default AccessType.RDF;
}
