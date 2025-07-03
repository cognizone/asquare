package zone.cogni.actionlogger;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface CreateReportInterceptor {

  void intercept(HttpServletRequest request, Map<String, Object> report);
}
