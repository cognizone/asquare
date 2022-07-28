package zone.cogni.asquare.security2.saml2;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Saml2Properties {

  private SigningKeys signingKeyStore;
  private String registrationId;
  private String idpUrl;
  private Attributes attributes;
  private String roleMappingUrl;
  private Map<String, User> basicAuthUsers = new HashMap<>(); //init so we allow empty config

  public enum KeyStoreType {JKS}

  @Data
  public static class SigningKeys {
    private KeyStoreType type;
    private String storeUrl;
    private String keystorePassword;
    private String alias;
    private String certificatePassword;
  }

  @Data
  public static class Attributes {
    private String firstname;
    private String lastname;
    private String displayname;
    private String roles;
    private String loginid;
    private String email;
  }

  @Data
  public static class User {
    private String password;
    private List<String> roles = new ArrayList<>(); //init so we allow empty config
  }
}
