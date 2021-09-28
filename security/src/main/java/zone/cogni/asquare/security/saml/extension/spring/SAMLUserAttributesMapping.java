package zone.cogni.asquare.security.saml.extension.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "saml.idp")
public class SAMLUserAttributesMapping {

  public final static String FIRSTNAME_PROPERTY = "firstname";
  public final static String LASTNAME_PROPERTY = "lastname";
  public final static String DISPLAYNAME_PROPERTY = "displayname";
  public final static String ROLES_PROPERTY = "roles";
  public final static String LOGINID_PROPERTY = "loginid";
  public final static String EMAIL_PROPERTY = "email";

  public final static String[] KNOWN_PROPERTIES = {FIRSTNAME_PROPERTY, LASTNAME_PROPERTY, ROLES_PROPERTY, DISPLAYNAME_PROPERTY, LOGINID_PROPERTY, EMAIL_PROPERTY};

  private HashMap<String, String> attributes;

  public HashMap<String, String> getAttributes() {
    return attributes;
  }

  public void setAttributes(HashMap<String, String> attributes) {
    this.attributes = attributes;
  }

  public String getAttributeNameFor(String property) {
    if (!hasAttributeName(property)) {
      return null;
    }
    return attributes.get(property);
  }

  public boolean hasAttributeName(String property) {
    if (attributes == null) {
      return false;
    }
    return attributes.containsKey(property);
  }
}
