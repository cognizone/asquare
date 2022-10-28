package zone.cogni.asquare.security2.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

@JsonSerialize(
  using = AuthenticationToken.TokenJsonSerializer.class
)
public class AuthenticationToken extends AbstractAuthenticationToken {
  private final String ivUser;
  private final Set<GrantedAuthority> grantedAuthorities = Collections.synchronizedSet(new HashSet());
  private String fullName;
  private String ldapGroup;
  private String email;
  private String telephoneNumber;

  public AuthenticationToken(String ivUser) {
    super((Collection) null);
    this.ivUser = ivUser.toUpperCase();
    this.setAuthenticated(false);
  }

  public Object getCredentials() {
    return null;
  }

  public Object getPrincipal() {
    return this.ivUser;
  }

  public String getIvUser() {
    return this.ivUser;
  }

  public String getFullName() {
    return null == this.fullName ? this.ivUser : this.fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public Optional<String> getLdapGroup() {
    return Optional.ofNullable(this.ldapGroup);
  }

  public void setLdapGroup(String ldapGroup) {
    this.ldapGroup = ldapGroup;
  }

  public String getTelephoneNumber() {
    return this.telephoneNumber;
  }

  public void setTelephoneNumber(String telephoneNumber) {
    this.telephoneNumber = telephoneNumber;
  }

  public String getEmail() {
    return this.email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Collection<GrantedAuthority> getAuthorities() {
    return this.grantedAuthorities;
  }

  public static class TokenJsonSerializer extends StdSerializer<AuthenticationToken> {
    public TokenJsonSerializer() {
      super(AuthenticationToken.class);
    }

    public void serialize(AuthenticationToken token, JsonGenerator gen, SerializerProvider provider) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("ivUser", token.ivUser);
      gen.writeStringField("fullName", token.fullName);
      gen.writeStringField("ldapGroup", token.ldapGroup);
      gen.writeStringField("email", token.email);
      gen.writeStringField("telephoneNumber", token.telephoneNumber);
      gen.writeArrayFieldStart("authorities");
      Iterator var4 = token.grantedAuthorities.iterator();

      while (var4.hasNext()) {
        GrantedAuthority authority = (GrantedAuthority) var4.next();
        gen.writeObject(authority);
      }

      gen.writeEndArray();
      gen.writeEndObject();
    }
  }
}
