package zone.cogni.asquare.security.model;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

public class AuthenticationTokenMapper {

  public static UserDto authTokenToUserDto(AuthenticationToken authenticationToken) {
    if (authenticationToken == null) {
      return null;
    }

    UserDto userDto = new UserDto();
    userDto.setFullName(authenticationToken.getFullName());
    userDto.setAccount(authenticationToken.getIvUser());

    return userDto;
  }

  public static UserDto authTokenToUserDto(String fullName, String ivUser) {
    UserDto userDto = new UserDto();
    userDto.setFullName(fullName);
    userDto.setAccount(ivUser);

    return userDto;
  }

  public static UserDto authTokenListToUserDto(List<AuthenticationToken> authenticationTokenList) {
    if (CollectionUtils.isEmpty(authenticationTokenList)) {
      return null;
    }
    return authTokenToUserDto(authenticationTokenList.get(0));
  }

  private AuthenticationTokenMapper() {
  }
}
