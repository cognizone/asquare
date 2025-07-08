package zone.cogni.asquare.service.async;

import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public class AsyncUtils {

  private static final Logger log = LoggerFactory.getLogger(AsyncUtils.class);
  
  // InheritableThreadLocal to store async context, which propagates to child threads
  private static final InheritableThreadLocal<Map<String, Object>> asyncContextHolder = new InheritableThreadLocal<>();

  private AsyncUtils() {
  }

  /**
   * Store async context in ThreadLocal
   */
  public static void setAsyncContext(Map<String, Object> context) {
    asyncContextHolder.set(context != null ? new HashMap<>(context) : null);
  }

  /**
   * Clear async context from ThreadLocal
   */
  public static void clearAsyncContext() {
    asyncContextHolder.remove();
  }

  /**
   * In Java 17+, we can't safely extract callables from FutureTask internals.
   * This method now returns the task as-is.
   */
  public static Object findCallable(Runnable task) {
    // Return task as-is without reflection
    return task;
  }

  /**
   * In Java 17+, we can't safely unwrap tasks using reflection.
   * This method now returns the task as-is.
   */
  public static Object findRealTask(Runnable task) {
    // Return task as-is without reflection
    return task;
  }

  public static AsyncContext findAsyncContextAnnotation(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      if (AsyncContext.class.isAssignableFrom(annotation.annotationType())) {
        return (AsyncContext) annotation;
      }
    }
    return null;
  }

  protected static MethodInvocation findMethodInvocation(Field field, Object dynamicAdvisedInterceptor) {
    Object methodInvocationAdvice = null;
    try {
      methodInvocationAdvice = field.get(dynamicAdvisedInterceptor);
    }
    catch (IllegalArgumentException | IllegalAccessException e) {
      return null;
    }
    if (methodInvocationAdvice == null) {
      return null;
    }
    if (!(methodInvocationAdvice instanceof MethodInvocation)) {
      return null;
    }

    return (MethodInvocation) methodInvocationAdvice;
  }

  public static Map<String, Object> findAsyncParams(Method method, Object[] params) {
    final Map<String, Object> map = new HashMap<>();

    Annotation[][] paramAnnotations = method.getParameterAnnotations();
    for (int i = 0; i < paramAnnotations.length; i++) {
      AsyncContext asyncContext = findAsyncContextAnnotation(paramAnnotations[i]);
      if (asyncContext != null) {
        map.put(asyncContext.value(), params[i]);
      }
    }
    return map;
  }

  private static Map<String, Object> findAsyncParams(MethodInvocation methodInvocation) {
    Method method = methodInvocation.getMethod();
    Object[] params = methodInvocation.getArguments();
    return findAsyncParams(method, params);
  }

  private static Method getMethod(ProceedingJoinPoint joinPoint) {
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    return methodSignature.getMethod();
  }

  public static Map<String, Object> getAsyncContext(ProceedingJoinPoint joinPoint) {
    Method method = getMethod(joinPoint);
    Object[] params = joinPoint.getArgs();
    return findAsyncParams(method, params);
  }

  /**
   * Get async context from ThreadLocal storage.
   * This replaces the reflection-based exploration of FutureTask internals.
   *
   * @param runnable - executed FutureTask executed by Spring TaskExecutor
   * @return collected async context values mapped by name
   */
  public static Map<String, Object> getAsyncContext(Runnable runnable) {
    // Get context from ThreadLocal instead of using reflection
    Map<String, Object> context = asyncContextHolder.get();
    return context != null ? new HashMap<>(context) : new HashMap<>();
  }

  public static <T> boolean timeoutWhileDone(CompletableFuture<T> future, int timeout) {
    return timeoutWhile(timeout, future::isDone);
  }

  public static <T> boolean timeoutWhileUndone(CompletableFuture<T> future, int timeout) {
    return timeoutWhile(timeout, () -> (!future.isDone()));
  }

  public static boolean timeoutWhileLock(AtomicBoolean lock, int timeout) {
    return timeoutWhile(timeout, lock::get);
  }

  public static boolean timeoutWhileUnlock(AtomicBoolean lock, int timeout) {
    return timeoutWhile(timeout, () -> (!lock.get()));
  }

  public static boolean timeoutWhile(int timeout, BooleanSupplier doWhile) {
    long start = System.currentTimeMillis();
    while (doWhile.getAsBoolean()) {
      if (System.currentTimeMillis() - start > timeout) {
        return true;
      }
      try {
        Thread.sleep(100);
      }
      catch (InterruptedException ex) {
        log.error("timeout was interrupted with {}", ex);
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }
}