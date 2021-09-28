package zone.cogni.asquare.security.basic.service;

import zone.cogni.asquare.security.basic.model.UserInfo;

public interface UserService {
  UserInfo getUserInfo(String userId);
}
