package zone.cogni.asquare.security.basic;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import zone.cogni.asquare.security.basic.service.UserService;
import zone.cogni.asquare.security.model.AuthenticationToken;

@Component("BasicAuthenticationProvider")
public class BasicAuthenticationProvider implements AuthenticationProvider {

  private final EncodedSecurityCredentials encodedSecurityCredentials;
  private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

  private final UserService localLdapQueryService;

  public BasicAuthenticationProvider(
    EncodedSecurityCredentials encodedSecurityCredentials,
    UserService localLdapQueryService) {
    this.encodedSecurityCredentials = encodedSecurityCredentials;
    this.localLdapQueryService = localLdapQueryService;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    if (authentication instanceof AuthenticationToken) {
      return authentication;
    }
    String userName = (String) authentication.getPrincipal();
    String presentedPassword = (String) authentication.getCredentials();

    if (encodedSecurityCredentials.getEncodedCredentials() != null &&
        encodedSecurityCredentials.getEncodedCredentials().containsKey(userName)) {
      if (encoder.matches(presentedPassword, encodedSecurityCredentials.getEncodedCredentials().get(userName))) {
        return localLdapQueryService.getUserInfo(authentication.getName()).convertToAuthenticationToken();
      }
    }

    throw new BadCredentialsException("Not allowed");
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return true;
  }
}
