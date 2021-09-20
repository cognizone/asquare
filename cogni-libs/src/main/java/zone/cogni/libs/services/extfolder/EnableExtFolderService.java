package zone.cogni.libs.services.extfolder;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(ExtFolderConfiguration.class)
public @interface EnableExtFolderService {
  /**
   * The system property containing the folder location.
   */
  String value() default "cognizone.ext.folder";
  boolean required() default true;
}
