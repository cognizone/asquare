package zone.cogni.asquare.security2.saml2;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import zone.cogni.core.spring.ResourceHelper;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

@RequiredArgsConstructor
public class RoleMappingService {
  private static final Logger log = LoggerFactory.getLogger(RoleMappingService.class);

  private final Saml2Properties saml2Properties;
  private Map<String, String> roleMapping;

  private void loadMapping() {
    if (null == roleMapping && StringUtils.isNotBlank(saml2Properties.getRoleMappingUrl())) {
      log.debug("Loading role map from : {}", saml2Properties.getRoleMappingUrl());
      Resource roleMappingResource = ResourceHelper.getResourceFromUrl(saml2Properties.getRoleMappingUrl());
      try (InputStream is = ResourceHelper.getInputStream(roleMappingResource)) {
        ObjectMapper mapper = new ObjectMapper();
        roleMapping = Collections.synchronizedMap((Map<String, String>) mapper.readValue(is, Map.class));
      }
      catch (Exception e) {
        log.error("Exception occurs while reading role mapping for SAML authentication system !", e);
      }
    }
  }

  public String getApplicationRoleFor(String samlRole) {
    loadMapping();
    return null == roleMapping ? null : roleMapping.get(samlRole);
  }

  public void forceReload() {
    roleMapping = null;
    loadMapping();
  }
}
