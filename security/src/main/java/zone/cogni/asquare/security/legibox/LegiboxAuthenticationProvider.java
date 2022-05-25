package zone.cogni.asquare.security.legibox;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import zone.cogni.asquare.security.model.AuthenticationToken;

@Component("BasicAuthenticationProvider")
public class LegiboxAuthenticationProvider implements AuthenticationProvider {

  private final LegiboxUserService myLegiboxUserService;
  private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public LegiboxAuthenticationProvider(LegiboxUserService myLegiboxUserService) {
    this.myLegiboxUserService = myLegiboxUserService;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    if (authentication instanceof AuthenticationToken) {
      return authentication;
    }
    String presentedPassword = (String) authentication.getCredentials();

    LegiboxUser myLegiboxUser = myLegiboxUserService.getUser(authentication.getName());
    String encryptedPassword = myLegiboxUser.getPassword();

    if (encoder.matches(presentedPassword, encryptedPassword)) {
      return myLegiboxUser;
    }

    throw new BadCredentialsException("Not allowed");
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return true;
  }
}
