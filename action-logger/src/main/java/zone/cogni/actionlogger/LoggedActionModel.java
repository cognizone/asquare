package zone.cogni.actionlogger;

import java.util.Map;

public class LoggedActionModel {

  public interface ReportKeys {
    String id = "id";
    String parentId = "parentId";
    String name = "name";
    String requestorName = "requestorName";
    String methodName = "methodName";
    String methodClass = "methodClass";
    String actionInfo = "actionInfo";
    String start = "start";
    String end = "end";
    String success = "success";
    String stackTraceValue = "stackTraceValue";   //to use in debugger aka analyse stacktrace
    String stackTrace = "stackTrace";   //for readability
    String errorMessage = "errorMessage";

    String httpRequestURL = "http.requestURL";
    String httpRequestURI = "http.requestURI";
    String httpHeaders = "http.headers";
    String httpMethod = "http.method";
    String httpRemoteHost = "http.remoteHost";
    String httpActionPath = "http.actionPath";
    String httpParameters = "http.parameters";
    String httpUserPrincipal = "http.userPrincipal";
    String httpGetFailed = "http.getFailed";
  }

  @Deprecated
  public interface LegacyKeys {
    String userIv = "userIv";
    String userFullName = "userFullName";
    String requestParams = "requestParams";
    String httpMethod = "httpMethod";
    String remoteHost = "remoteHost";
    String path = "path";
  }

  private static final ThreadLocal<Map<String, Object>> actionInfoThreadLocal = new ThreadLocal<>();

  private LoggedActionModel() {
  }

  public static Map<String, Object> getActionInfo() {
    return actionInfoThreadLocal.get();
  }

  static ThreadLocal<Map<String, Object>> getActionInfoThreadLocal() {
    return actionInfoThreadLocal;
  }

}
