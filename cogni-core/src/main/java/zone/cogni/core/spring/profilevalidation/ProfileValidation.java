package zone.cogni.core.spring.profilevalidation;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(ProfileValidationExecutor.class)
public @interface ProfileValidation {

  Disjoint[] disjoint() default {};

}
