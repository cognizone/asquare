package zone.cogni.asquareroot.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class WebSecurityTestConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
            .httpBasic(httpBasic -> httpBasic.realmName("casemates-app-test"))
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(new AntPathRequestMatcher("/api/secured/**")).authenticated()
                    .anyRequest().permitAll() // Add this line to permit other requests if needed
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sessionManagement -> sessionManagement
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
    return http.build();
  }

  @Bean
  public UserDetailsService userDetailsService() {
    UserDetails testUser = User.withUsername("testUser")
                               .password("{noop}testPassword") // Use {noop} for plain text in tests only
                               .roles("USER")
                               .build();
    UserDetails managerUser = User.withUsername("manager")
                                  .password("{noop}password")
                                  .credentialsExpired(true)
                                  .accountExpired(true)
                                  .accountLocked(true)
                                  .authorities("WRITE_PRIVILEGES", "READ_PRIVILEGES")
                                  .roles("MANAGER")
                                  .build();
    return new InMemoryUserDetailsManager(testUser, managerUser);
  }
}
