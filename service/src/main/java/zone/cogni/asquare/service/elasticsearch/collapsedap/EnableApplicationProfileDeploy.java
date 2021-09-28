package zone.cogni.asquare.service.elasticsearch.collapsedap;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)

@Target(ElementType.TYPE)

@Import(ApplicationProfileDeployConfiguration.class)


public @interface EnableApplicationProfileDeploy {

  String indexName() default "config";

  String indexDocumentId();

  String apResource();

}