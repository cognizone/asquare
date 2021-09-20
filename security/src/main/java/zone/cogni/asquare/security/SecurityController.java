package zone.cogni.asquare.security;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zone.cogni.asquare.security.model.AuthenticationToken;
import zone.cogni.asquare.security.model.AuthenticationTokenMapper;
import zone.cogni.asquare.security.model.UserDto;

@RestController
@RequestMapping("/api/public")
public class SecurityController {

  private final MappingJackson2HttpMessageConverter httpMessageConverter;

  public SecurityController(MappingJackson2HttpMessageConverter httpMessageConverter) {
    this.httpMessageConverter = httpMessageConverter;
  }

  @GetMapping("user")
  public UserDto getUser(Authentication authentication) {
    if (authentication == null) {
      return null;
    }

    //if (authentication == null) {
    //  return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    //}

    UserDto userDto;
    if (authentication instanceof AuthenticationToken) {
      userDto = AuthenticationTokenMapper.authTokenToUserDto((AuthenticationToken) authentication);
    }
    else {
      userDto = AuthenticationTokenMapper.authTokenToUserDto(authentication.getName(), authentication.getName());
    }
    return userDto;//ResponseEntity.ok().body(RestResponse.success(userDto));
  }
}
