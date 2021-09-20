package zone.cogni.libs.services.extfolder;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import java.io.File;
import java.util.Map;

@Configuration
public class ExtFolderConfiguration implements ImportAware {

  @Autowired
  private Environment env;

  private File extFolder;
  private boolean required;

  @SuppressWarnings("unchecked")
  @Override
  public void setImportMetadata(AnnotationMetadata importMetadata) {
    Map<String, Object> annotationAttributes = Preconditions.checkNotNull(importMetadata.getAnnotationAttributes(EnableExtFolderService.class.getName()), "No EnableExtFolderService annotations found");
    String extFolderEnvironmentProperty = Preconditions.checkNotNull((String) annotationAttributes.get("value"), "Value of EnableExtFolderService is null.");
    String extFolderPath = env.getProperty(extFolderEnvironmentProperty);
    Preconditions.checkState(StringUtils.isNotBlank(extFolderPath), "Value of EnableExtFolderService not found as SystemProperty: %s", extFolderEnvironmentProperty);
    extFolder = new File(extFolderPath);
    required = (boolean)annotationAttributes.get("required");
    if (!(boolean)annotationAttributes.get("required") && !extFolder.exists()) {
      if (!extFolder.mkdirs()) {
        throw new RuntimeException("Unable to create extFolder with path '" + extFolderPath + "'");
      }
    }
    Preconditions.checkState(extFolder.isDirectory(), "SystemProperty '%s' (Value of EnableExtFolderService) does not point to a path: %s", extFolderEnvironmentProperty, extFolderPath);
  }

  @Bean
  public ExtFolderServiceFactory extFolderServiceFactory() {
    return new ExtFolderServiceFactory(extFolder, required);
  }
}
