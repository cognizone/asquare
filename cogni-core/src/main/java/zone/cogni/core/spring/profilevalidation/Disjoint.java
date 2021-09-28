package zone.cogni.core.spring.profilevalidation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Disjoint {

  /**
   * List of profiles that can not exist together.
   */
  String[] value();

  /**
   * True if at least 1 of the profiles need to be set.
   */
  boolean mandatory() default false;
}
