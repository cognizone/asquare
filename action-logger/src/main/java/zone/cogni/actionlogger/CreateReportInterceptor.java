package zone.cogni.actionlogger;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public interface CreateReportInterceptor {

  void intercept(HttpServletRequest request, Map<String, Object> report);
}
