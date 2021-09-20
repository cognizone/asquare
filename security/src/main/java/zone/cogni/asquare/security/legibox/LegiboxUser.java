package zone.cogni.asquare.security.legibox;

import zone.cogni.asquare.security.model.AuthenticationToken;

public abstract class LegiboxUser extends AuthenticationToken {
  public LegiboxUser(String ivUser) {
    super(ivUser);
  }

  public abstract String getPassword();
  public abstract boolean isActive();
  public abstract void setLastActivity(String lastActivity);

}
