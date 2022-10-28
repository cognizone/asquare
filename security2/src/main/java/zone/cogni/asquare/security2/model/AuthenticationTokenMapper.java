package zone.cogni.asquare.security2.model;

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

  private AuthenticationTokenMapper() {
  }
}
