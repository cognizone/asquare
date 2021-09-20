package zone.cogni.asquareroot.security;

import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@EnableWebSecurity
public class WebSecurityTestConfig extends WebSecurityConfigurerAdapter {

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.httpBasic().realmName("casemates-app-test").and().authorizeRequests()
        .requestMatchers(new AntPathRequestMatcher("/api/secured/**")).authenticated().and().csrf().disable()
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
  }

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {

    auth.inMemoryAuthentication().withUser("testUser").password("{noop}testPassword").roles("USER").and()
        .withUser("manager").password("{noop}password").credentialsExpired(true).accountExpired(true)
        .accountLocked(true).authorities("WRITE_PRIVILEGES", "READ_PRIVILEGES").roles("MANAGER");
  }

}
