package zone.cogni.asquare.security.legibox;

public interface LegiboxUserService {
  LegiboxUser getUser(String id);

  void putUser(LegiboxUser user);
}
