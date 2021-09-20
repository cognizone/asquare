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

    //TODO: to implement delegated password encoder with custom support for existing md5 encoded passwords
    if ((StringUtils.startsWith(encryptedPassword, "{bcrypt}") && encoder.matches(presentedPassword, encryptedPassword)) ||
        StringUtils.equals(DigestUtils.md5Hex(presentedPassword), encryptedPassword)) {//md5hex to cover old legilux passwords (future ones are bcrypt)
      return myLegiboxUser;
    }

    throw new BadCredentialsException("Not allowed");
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return true;
  }
}
