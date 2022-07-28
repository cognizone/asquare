package zone.cogni.asquare.security2.saml2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.www.BasicAuthenticationConverter;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class BasicAuthHandler {

  //Should we Beanize these 2 ?
  private static final BasicAuthenticationConverter basicAuthenticationConverter = new BasicAuthenticationConverter();
  private static final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

  private final Saml2Properties saml2Properties;

  public void handle(HttpServletRequest request) {
    Authentication currentAuthentication = SecurityContextHolder.getContext().getAuthentication();
    if (null != currentAuthentication && currentAuthentication.isAuthenticated()) return;

    UsernamePasswordAuthenticationToken requestUsernamePassword = parseRequest(request);
    if (null == requestUsernamePassword) return;

    Saml2Properties.User user = saml2Properties.getBasicAuthUsers().get(requestUsernamePassword.getName());
    if (null == user) return;

    if (!passwordEncoder.matches((CharSequence) requestUsernamePassword.getCredentials(), user.getPassword())) return;

    List<SimpleGrantedAuthority> authorities = user.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    Authentication authentication = new UsernamePasswordAuthenticationToken(requestUsernamePassword.getName(), "*******", authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private UsernamePasswordAuthenticationToken parseRequest(HttpServletRequest request) {
    try {
      return basicAuthenticationConverter.convert(request);
    }
    catch (Exception ex) { //can happen if somebody sends weirdo Authorization header... we will just ignore
      log.warn("Failed to convert basic-auth: {}", ex.getMessage());
      return null;
    }
  }
}
