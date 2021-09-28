package zone.cogni.asquare.security.legibox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import zone.cogni.asquare.service.rest.model.RestResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component("BasicAuthenticationSuccessHandler")
public class LegiboxAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
  private static final Logger log = LoggerFactory.getLogger(LegiboxAuthenticationSuccessHandler.class);
  private final MappingJackson2HttpMessageConverter httpMessageConverter;
  private final LegiboxUserService myLegiboxUserService;
  @Value("${mylegibox.dateformat}")
  private String dateFormat;

  public LegiboxAuthenticationSuccessHandler(MappingJackson2HttpMessageConverter httpMessageConverter,
                                             LegiboxUserService myLegiboxUserService) {
    this.httpMessageConverter = httpMessageConverter;
    this.myLegiboxUserService = myLegiboxUserService;
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

    processAndUpdateUser(response, userDto.getAccount());

    HttpOutputMessage outputMessage = new ServletServerHttpResponse(response);
    httpMessageConverter.write(userDto, MediaType.APPLICATION_JSON, outputMessage);
    response.setStatus(HttpStatus.OK.value());
  }

  private void processAndUpdateUser(HttpServletResponse response, String identifier) {
    LegiboxUser user = myLegiboxUserService.getUser(identifier);

    //check account activation status
    if (!user.isActive()) handleNotActivatedResponse(response, identifier);

    //set last login activity
    user.setLastActivity(ZonedDateTime.now().format(DateTimeFormatter.ofPattern(dateFormat)));

    myLegiboxUserService.putUser(user);
  }

  private void handleNotActivatedResponse(HttpServletResponse response, String identifier) {
    response.setStatus(HttpStatus.OK.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    byte[] json = Try.of(() -> new ObjectMapper().writeValueAsBytes(RestResponse.error(String.format("Account %s not active!", identifier), "exception.legibox.account_not_activated")))
                     .getOrElse(() -> new byte[0]);
    try {
      response.getOutputStream().write(json);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    response.setContentLength(json.length);
  }

}

