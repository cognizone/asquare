package zone.cogni.actionlogger;

import org.apache.commons.collections4.EnumerationUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static zone.cogni.actionlogger.LoggedActionModel.LegacyKeys;
import static zone.cogni.actionlogger.LoggedActionModel.ReportKeys;
import static zone.cogni.actionlogger.LoggedActionModel.getActionInfoThreadLocal;

@Aspect
@Component
public class LoggedActionAspect {

  private static final Logger log = LoggerFactory.getLogger(LoggedActionAspect.class);

  private static final ThreadLocal<Map<String, Object>> reportThreadLocal = new ThreadLocal<>();

  private final LoggedActionSaver loggedActionSaver;
  private final List<CreateReportInterceptor> createReportInterceptors;

  public LoggedActionAspect(LoggedActionSaver loggedActionSaver,
                            @Autowired(required = false) List<CreateReportInterceptor> createReportInterceptors) {
    this.loggedActionSaver = loggedActionSaver;
    this.createReportInterceptors = null == createReportInterceptors ? Collections.emptyList() : createReportInterceptors;
  }

  @Around("@annotation(zone.cogni.actionlogger.LoggedAction)")
  public Object logAction(ProceedingJoinPoint joinPoint) throws Throwable {
    return new ActionInstance(joinPoint).logAction();
  }

  private class ActionInstance {
    private final ProceedingJoinPoint joinPoint;
    private final Map<String, Object> report = new HashMap<>();
    private final Map<String, Object> parentReport;
    private final Map<String, Object> parentActionInfo;
    private final Method method;
    private final LoggedAction loggedActionAnnotation;

    private ActionInstance(ProceedingJoinPoint joinPoint) {
      this.joinPoint = joinPoint;
      parentReport = reportThreadLocal.get();
      parentActionInfo = getActionInfoThreadLocal().get();

      MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
      method = methodSignature.getMethod();
      loggedActionAnnotation = method.getAnnotation(LoggedAction.class);
    }

    private Object logAction() throws Throwable {
      initReport();
      if (loggedActionAnnotation.takesLongTime()) storeReport(report);
      try {
        Object actionResult = joinPoint.proceed();
        report.put(ReportKeys.success, true);
        return actionResult;
      }
      catch (Throwable exception) {
        report.put(ReportKeys.success, false);
        report.put(ReportKeys.stackTraceValue, ExceptionUtils.getStackTrace(exception));  //to use in debugger aka analyse stacktrace
        report.put(ReportKeys.stackTrace, ExceptionUtils.getStackFrames(exception)); //for readability
        report.put(ReportKeys.errorMessage, exception.getMessage());
        //noinspection ProhibitedExceptionThrown
        throw exception;
      }
      finally {
        report.put(ReportKeys.end, Instant.now().toString());
        cleanup();
        storeReport(report);
      }
    }

    private void cleanup() {
      reportThreadLocal.remove();
      getActionInfoThreadLocal().remove();

      if (null != parentReport) reportThreadLocal.set(parentReport);
      if (null != parentActionInfo) getActionInfoThreadLocal().set(parentActionInfo);
    }

    private void storeReport(Map<String, Object> report) {
      try {
        loggedActionSaver.save(report);
      }
      catch (Exception e) {
        log.warn("Failed to save report", e);
      }
    }

    private void initReport() {
      report.put(ReportKeys.start, Instant.now().toString());
      report.put(ReportKeys.id, UUID.randomUUID().toString());
      if (null != parentReport) report.put(ReportKeys.parentId, parentReport.get(ReportKeys.id));

      findParameterValue(method, LoggedActionRequestor.class)
        .map(Objects::toString)
        .ifPresent(value -> report.put(ReportKeys.requestorName, value));

      report.put(ReportKeys.name, loggedActionAnnotation.value());
      report.put(ReportKeys.methodName, method.getName());
      report.put(ReportKeys.methodClass, method.getDeclaringClass().getName());
      addHttpRequestInfo();

      Map<String, Object> actionInfo = new HashMap<>();
      report.put(ReportKeys.actionInfo, actionInfo);

      getActionInfoThreadLocal().set(actionInfo);
      reportThreadLocal.set(report);
    }

    private Optional<Object> findParameterValue(Method method, Class<? extends Annotation> annotationClass) {
      for (int i = 0; i < method.getParameterAnnotations().length; i++) {
        boolean hasAnnotation = Arrays.stream(method.getParameterAnnotations()[i]).anyMatch(annotationClass::isInstance);
        if (hasAnnotation) return Optional.ofNullable(Objects.toString(joinPoint.getArgs()[i]));
      }
      return Optional.empty();
    }

    private void addHttpRequestInfo() {
      RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
      if (requestAttributes == null) return;

      if (!(requestAttributes instanceof ServletRequestAttributes)) {
        report.put(ReportKeys.httpGetFailed, "getRequestAttributes is not of type ServletRequestAttributes");
        return;
      }

      HttpServletRequest httpRequest = ((ServletRequestAttributes) requestAttributes).getRequest();

      Principal userPrincipal = httpRequest.getUserPrincipal();
      if (null != userPrincipal) {
        report.put(ReportKeys.httpUserPrincipal, userPrincipal.getName());
        report.put(LegacyKeys.userIv, getIvUser(userPrincipal));
        report.put(LegacyKeys.userFullName, getFullName(userPrincipal));
      }
      report.put(ReportKeys.httpRequestURI, httpRequest.getRequestURI());
      report.put(ReportKeys.httpRequestURL, httpRequest.getRequestURL().toString());
      Map<String, List<String>> headerValues =
        EnumerationUtils.toList(httpRequest.getHeaderNames()).stream()
                        .collect(Collectors.toMap(headerName -> headerName,
                                                  headerName -> EnumerationUtils.toList(httpRequest.getHeaders(headerName)))
                        );
      report.put(ReportKeys.httpHeaders, headerValues);
      report.put(ReportKeys.httpMethod, httpRequest.getMethod());
      report.put(LegacyKeys.httpMethod, httpRequest.getMethod());
      report.put(ReportKeys.httpRemoteHost, httpRequest.getRemoteHost());
      report.put(LegacyKeys.remoteHost, httpRequest.getRemoteHost());
      report.put(ReportKeys.httpActionPath, httpRequest.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE));
      report.put(LegacyKeys.path, httpRequest.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE));
      report.put(ReportKeys.httpParameters, httpRequest.getParameterMap());
      report.put(LegacyKeys.requestParams, httpRequest.getParameterMap());

      // custom report interceptors may override default settings
      createReportInterceptors.forEach(interceptor -> {
        try {
          interceptor.intercept(httpRequest, report);
        }
        catch(Exception ex) {
          log.error("Logging report interceptor {} has thrown an exception: {}", interceptor, ex);
        }
      });

    }
  }

  // TODO to be replaced with CreateReportInterceptor
  private Object getFullName(Principal userPrincipal) {
    try {
      return userPrincipal.getClass().getDeclaredMethod("getFullName").invoke(userPrincipal);
    }
    catch (Exception exception) {
      return "";
    }
  }

  // TODO to be replaced with CreateReportInterceptor
  private Object getIvUser(Principal userPrincipal) {
    try {
      return userPrincipal.getClass().getDeclaredMethod("getIvUser").invoke(userPrincipal);
    }
    catch (Exception exception) {
      return "";
    }
  }
}