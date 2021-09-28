package zone.cogni.asquare.security.saml.extension.spring;

import org.opensaml.saml2.core.Attribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.saml.SAMLCredential;
import zone.cogni.asquare.security.saml.extension.service.RoleMappingService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SAMLUserDetails implements UserDetails {
  private static final Logger log = LoggerFactory.getLogger(SAMLUserDetails.class);

  private String username;
  private List<GrantedAuthority> authorities = new ArrayList<>();

  private String firstname;
  private String lastname;
  private String loginid;
  private String displayName;
  private String email;

  public SAMLUserDetails(SAMLCredential samlCredential, SAMLUserAttributesMapping samlUserAttributesMapping, RoleMappingService roleMappingService) {
    this.username = samlCredential.getNameID().getValue();
    log.info("SAML logged userID : " + this.username);

    if (samlUserAttributesMapping != null) {
      for (String property : SAMLUserAttributesMapping.KNOWN_PROPERTIES) {
        String attributeName = samlUserAttributesMapping.getAttributeNameFor(property);
        if (attributeName != null) {
          Attribute attr = samlCredential.getAttribute(attributeName);
          if (attr != null) {
            if (property.equals(SAMLUserAttributesMapping.FIRSTNAME_PROPERTY)) {
              this.firstname = samlCredential.getAttributeAsString(attributeName);
            }
            if (property.equals(SAMLUserAttributesMapping.LASTNAME_PROPERTY)) {
              this.lastname = samlCredential.getAttributeAsString(attributeName);
            }
            if (property.equals(SAMLUserAttributesMapping.LOGINID_PROPERTY)) {
              this.loginid = samlCredential.getAttributeAsString(attributeName);
            }
            if (property.equals(SAMLUserAttributesMapping.DISPLAYNAME_PROPERTY)) {
              this.displayName = samlCredential.getAttributeAsString(attributeName);
            }
            if (property.equals(SAMLUserAttributesMapping.EMAIL_PROPERTY)) {
              this.email = samlCredential.getAttributeAsString(attributeName);
            }
            if (property.equals(SAMLUserAttributesMapping.ROLES_PROPERTY)) {
              String roleNames[] = samlCredential.getAttributeAsStringArray(attributeName);
              for (String roleName : roleNames) {
                String appRole = roleMappingService.getApplicationRoleFor(roleName);
                if (appRole != null) {
                  if (appRole.length() > 1) {
                    GrantedAuthority authority = new SimpleGrantedAuthority(appRole);
                    authorities.add(authority);
                  }
                }
              }
            }
          }
        }
      }
    }
    else {
      log.warn("IDP attributes mapping is null ! Perhaps configuration is missing");
    }
  }

  public String getFirstname() {
    return firstname;
  }

  public String getLastname() {
    return lastname;
  }

  public String getLoginid() {
    return loginid;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getEmail() {
    return email;
  }

  public Collection<? extends GrantedAuthority> setPrivileges(final List<String> privileges) {
    authorities.clear();
    for (String privilege : privileges) {
      authorities.add(new SimpleGrantedAuthority(privilege));
    }
    return authorities;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return "<SAML User>";
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
