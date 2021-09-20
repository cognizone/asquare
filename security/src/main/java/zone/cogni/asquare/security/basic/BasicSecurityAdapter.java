package zone.cogni.asquare.security.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.InMemoryUserDetailsManagerConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import zone.cogni.asquare.security.service.PermissionService;

import javax.inject.Inject;
import java.util.LinkedHashMap;

@Configuration
public class BasicSecurityAdapter extends WebSecurityConfigurerAdapter implements WebMvcConfigurer {

  private static final Logger log = LoggerFactory.getLogger(BasicSecurityAdapter.class);

  private final RequestMatcher protectedRequestMatcher;
  private final PermissionService<Enum> permissionService;
  private final EncodedSecurityCredentials encodedSecurityCredentials;
  private final AuthenticationSuccessHandler authenticationSuccessHandler;
  private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  private final AuthenticationProvider casematesAuthenticationProvider;

  @Value("${cognizone.security.realmName:cognizone-app}")
  private String realmName;
  @Inject
  public BasicSecurityAdapter(PermissionService permissionService,
                              @Qualifier("BasicAuthenticationSuccessHandler") AuthenticationSuccessHandler authenticationSuccessHandler,
                              EncodedSecurityCredentials encodedSecurityCredentials,
                              @Qualifier("BasicAuthenticationProvider") AuthenticationProvider casematesAuthenticationProvider,
                              @Qualifier("BasicProtectedRequestMatcher") RequestMatcher protectedRequestMatcher) {
    this.encodedSecurityCredentials = encodedSecurityCredentials;
    log.info("LocalSecurityConfig v1.1");
    this.permissionService = permissionService;
    this.authenticationSuccessHandler = authenticationSuccessHandler;
    this.casematesAuthenticationProvider = casematesAuthenticationProvider;

    this.protectedRequestMatcher = protectedRequestMatcher;
  }

  @Inject
  public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
    InMemoryUserDetailsManagerConfigurer<AuthenticationManagerBuilder> authenticationConfiguration = auth.authenticationProvider(casematesAuthenticationProvider)
                                                                                                         .inMemoryAuthentication();

    for (String oneRole : permissionService.getRoleNames()) {
      if (encodedSecurityCredentials.getEncodedCredentials() != null && encodedSecurityCredentials.getEncodedCredentials()
                                                                                                  .containsKey(oneRole)) {
        authenticationConfiguration = authenticationConfiguration
          .withUser(oneRole).password(encodedSecurityCredentials.getEncodedCredentials().get(oneRole)).roles(oneRole)
          .and();
      }
    }
  }

  public AuthenticationEntryPoint delegatingEntryPoint() {
    final LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> map = new LinkedHashMap();
    map.put(protectedRequestMatcher, new BasicAuthenticationEntryPoint());

    final DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(map);
    entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));

    return entryPoint;
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.httpBasic()
        .realmName(realmName)
        .and().authorizeRequests().requestMatchers(protectedRequestMatcher).authenticated()
        .and()
        .formLogin()
        .loginProcessingUrl("/login")
        .successHandler(authenticationSuccessHandler)
        .and()
        .logout()
        .logoutUrl("/logout")
        .clearAuthentication(true)
        .invalidateHttpSession(true)
        .deleteCookies("JSESSIONID")
        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(delegatingEntryPoint())
        .and()
        .cors()
        .and()
        .csrf()
        .disable()
        .httpBasic();
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
      .addMapping("/**")
      .allowedOrigins("*")
      .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD")
      .allowCredentials(true);
  }


}
