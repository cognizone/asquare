package zone.cogni.asquare.security.saml.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;

@Service
public class RoleMappingService {
    private static final Logger log = LoggerFactory.getLogger(RoleMappingService.class);

    @Value("${saml.sp.roleMapping:classpath:/saml/samlRoleMapping.json}")
    private String roleMappingResourceName;

    private HashMap<String, String> roleMapping;

    private void loadMapping() {
        if (roleMapping == null) {
            log.debug("Loading role map from : {}", roleMappingResourceName);
            try {
                ObjectMapper mapper = new ObjectMapper();
                InputStream is = new ClassPathResource(roleMappingResourceName).getInputStream();
                roleMapping = new HashMap<>();
                roleMapping = mapper.readValue(is, HashMap.class);
            } catch (Exception e) {
                log.error("Exception occurs while reading role mapping for SAML authentication system !");
            }
        }
    }

    public String getApplicationRoleFor(String samlRole) {
        loadMapping();
        return roleMapping.get(samlRole);
    }

    public void forceReload() {
        roleMapping = null;
        loadMapping();
    }
}
