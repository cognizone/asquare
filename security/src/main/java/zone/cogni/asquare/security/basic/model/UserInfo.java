package zone.cogni.asquare.security.basic.model;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import zone.cogni.asquare.security.model.AuthenticationToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserInfo {

  private final long validTillTime;
  private final List<SimpleGrantedAuthority> authorities = Collections.synchronizedList(new ArrayList<>());
  private final String id;
  private final String fullName;
  private final String ldapGroup;
  private final String email;
  private final String telephoneNumber;

  public UserInfo(String id, String fullName, String ldapGroup, String email, String telephoneNumber, List<SimpleGrantedAuthority> authorities) {
    validTillTime = Long.MAX_VALUE;
    this.id = id;
    this.fullName = fullName;
    this.ldapGroup = ldapGroup;
    this.email = email;
    this.telephoneNumber = telephoneNumber;
    this.authorities.addAll(authorities);
  }

  public String getId() {
    return id;
  }

  private long calculateValidTillTime(long cacheMaxAge) {
    if (0 == cacheMaxAge) return 0L; //already invalid
    if (-1 == cacheMaxAge) return Long.MAX_VALUE; //valid till end of times (sun explodes and stuff like that)
    return System.currentTimeMillis() + cacheMaxAge;
  }

  public boolean isValid() {
    return validTillTime >= System.currentTimeMillis();
  }

  public AuthenticationToken convertToAuthenticationToken() {
    AuthenticationToken authenticationToken = new AuthenticationToken(id);
    setInfo(authenticationToken);
    return authenticationToken;
  }

  public void setInfo(AuthenticationToken auth) {
    auth.getAuthorities().addAll(authorities);
    auth.setFullName(fullName);
    auth.setLdapGroup(ldapGroup);
    auth.setEmail(email);
    auth.setTelephoneNumber(telephoneNumber);
  }
}
