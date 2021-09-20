package zone.cogni.asquare.security.basic;

import com.google.common.collect.Lists;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import zone.cogni.asquare.security.model.AuthenticationToken;
import zone.cogni.asquare.security.model.AuthenticationTokenMapper;
import zone.cogni.asquare.security.model.UserDto;
import zone.cogni.asquare.security.service.PermissionService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;

@Component("BasicAuthenticationSuccessHandler")
public class BasicAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

  private final MappingJackson2HttpMessageConverter httpMessageConverter;
  private final PermissionService permissionService;

  public BasicAuthenticationSuccessHandler(MappingJackson2HttpMessageConverter httpMessageConverter,
                                           PermissionService permissionService) {
    this.httpMessageConverter = httpMessageConverter;
    this.permissionService = permissionService;
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                      Authentication authentication) throws IOException {
    UserDto userDto;
    if (authentication instanceof AuthenticationToken) {
      userDto = AuthenticationTokenMapper.authTokenToUserDto((AuthenticationToken) authentication);
    }
    else {
      userDto = AuthenticationTokenMapper.authTokenToUserDto(authentication.getName(), authentication.getName());
    }

    userDto.setRoles(permissionService.getRoles(authentication));
    userDto.setPermissions(new HashSet<>());

    Lists.newArrayList(userDto.getRoles()).forEach(role -> {
      permissionService.getProjectPermissions(role).forEach(p->{
        userDto.getPermissions().add(p.toString());
      });
    });

    HttpOutputMessage outputMessage = new ServletServerHttpResponse(response);
    httpMessageConverter.write(userDto, MediaType.APPLICATION_JSON, outputMessage);
    response.setStatus(HttpStatus.OK.value());
  }
}

