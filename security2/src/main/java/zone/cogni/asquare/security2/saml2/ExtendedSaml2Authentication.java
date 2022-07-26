package zone.cogni.asquare.security2.saml2;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;

import java.util.Collection;

public class ExtendedSaml2Authentication extends AbstractAuthenticationToken {
  private Saml2Authentication saml2Authentication;

  public ExtendedSaml2Authentication(Collection<GrantedAuthority> authorities, Saml2Authentication saml2Authentication) {
    super(authorities);
    this.saml2Authentication = saml2Authentication;
    setAuthenticated(true);
  }

  @Override
  public Object getPrincipal() {
    return saml2Authentication.getPrincipal();
  }

  public String getSaml2Response() {
    return saml2Authentication.getSaml2Response();
  }

  @Override
  public Object getCredentials() {
    return saml2Authentication.getSaml2Response();
  }

}
