package zone.cogni.asquare.security.saml.extension.entity;

import org.opensaml.saml2.metadata.LocalizedString;

/**
 * SAML Organisation Entity
 *
 * @author Patrick Lezy
 * @see org.opensaml.saml2.metadata.Organization
 */

public class CHOrganization {
  private LocalizedString name;
  private LocalizedString displayName;
  private LocalizedString url;

  public CHOrganization(LocalizedString name, LocalizedString displayName, LocalizedString url) {
    this.name = name;
    this.displayName = displayName;
    this.url = url;
  }

  public CHOrganization() {
  }

  public LocalizedString getName() {
    return name;
  }

  public void setName(LocalizedString name) {
    this.name = name;
  }

  public LocalizedString getDisplayName() {
    return displayName;
  }

  public void setDisplayName(LocalizedString displayName) {
    this.displayName = displayName;
  }

  public LocalizedString getUrl() {
    return url;
  }

  public void setUrl(LocalizedString url) {
    this.url = url;
  }
}
