package zone.cogni.libs.sparqlservice;

import com.google.common.base.Preconditions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

@Configuration
public class SparqlConfiguration implements ImportAware {

  private String configPrefix;

  @SuppressWarnings("unchecked")
  @Override
  public void setImportMetadata(AnnotationMetadata importMetadata) {
    Map<String, Object> annotationAttributes = Preconditions.checkNotNull(importMetadata.getAnnotationAttributes(EnableSparqlServer.class.getName()), "No EnableSparqlServer annotations found");
    configPrefix = (String) annotationAttributes.get("value");
  }


  @Bean
  public SparqlServiceProvider sparqlServiceProvider() {
    return new SparqlServiceProvider(configPrefix);
  }

}
