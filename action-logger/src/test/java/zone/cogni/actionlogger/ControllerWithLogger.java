package zone.cogni.actionlogger;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ControllerWithLogger {

  @RequestMapping("test")
  @ResponseBody
  @LoggedAction
  public String test() {
    return "ok";
  }
}
