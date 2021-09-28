package zone.cogni.asquare.transactional;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * annotation used by
 * @see ASquareTransactionalAspect
 **/
@Retention(RetentionPolicy.RUNTIME)
public @interface ASquareTransactional {
  ASquareTransactionType value();
}
