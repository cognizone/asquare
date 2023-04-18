package zone.cogni.asquare.service.async;

import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public class AsyncUtils {

  private static final Logger log = LoggerFactory.getLogger(AsyncUtils.class);
  private static final Class<? extends Callable> adapterClass;
  private static final Class workerClass;
  private static final Field callableInFutureTask;
  private static final Field runnableInAdapter;
  private static final Field taskInWorker;
  private static String workerClassName = "java.util.concurrent.ThreadPoolExecutor$Worker";

  static {
    // Preparing accessors for callable task and runnable adapter of a proxied async service method call
    try {
      callableInFutureTask = FutureTask.class.getDeclaredField("callable");
      callableInFutureTask.setAccessible(true);//NOSONAR making accessible dummy callableInFutureTask is safe
      Runnable runnable = () -> {
        throw new UnsupportedOperationException();
      };
      adapterClass = Executors.callable(runnable).getClass();
      runnableInAdapter = adapterClass.getDeclaredField("task");
      runnableInAdapter.setAccessible(true);//NOSONAR making accessible dummy runnableInAdapter is safe

      workerClass = Arrays.stream(ThreadPoolExecutor.class.getDeclaredClasses()).filter(c -> workerClassName.equals(c.getName()))
                          .findFirst()
                          .orElseThrow(() -> new NoClassDefFoundError("Can not get Worker class definition from ThreadPoolExecutor."));

      taskInWorker = workerClass.getDeclaredField("firstTask");
      taskInWorker.setAccessible(true);//NOSONAR making accessible dummy taskInWorker is safe
    }
    catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private AsyncUtils() {
  }

  public static Object findCallable(Runnable task) throws IllegalAccessException {
    if (task instanceof FutureTask) {
      return callableInFutureTask.get(task);
    }
    else if (workerClass.isInstance(task)) {
      return taskInWorker.get(task);
    }
    throw new ClassCastException("Only callable FutureTask and thread Worker are supported");
  }

  public static Object findRealTask(Runnable task) {
    try {
      Object callable = findCallable(task);
      if (adapterClass.isInstance(callable)) {
        return runnableInAdapter.get(callable);
      }
      else {
        return callable;
      }
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
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
   * Method is trying to explore executed task to find and collect map of method parameters annotated with
   *
   * @param runnable - executed FutureTask executed by Spring TaskExecutor
   * @return collected async context values mapped by name
   * @AsyncContext and mapping their values by name.
   * <p>
   * For example for execution someService.serviceMethod("this is 1", "this is 2") of given "serviceMethod" getAsyncContext will produce map {"var1":"this is 1", "var2":"this is 2"}
   * public void serviceMethod(@AsyncContext("var1") String v1, @AsyncContext("var2") String v2) { ... }
   */
  public static Map<String, Object> getAsyncContext(Runnable runnable) {
    // Need to recover real task because spring makes dynamic proxy adapter around callable serviceMethod
    final Object dynamicAdvisedInterceptor = findRealTask(runnable);

    // Try to collect @AsyncContext parameters over all proxied methods
    for (Field field : dynamicAdvisedInterceptor.getClass().getDeclaredFields()) {
      field.setAccessible(true);//NOSONAR safe attempt to try set accessible
      if (field.isAccessible()) {//TODO replace it with trySetAccessible when this code will be migrated to Java 11
        MethodInvocation methodInvocation = findMethodInvocation(field, dynamicAdvisedInterceptor);
        if (methodInvocation != null) {
          return findAsyncParams(methodInvocation);
        }
      }
    }
    return new HashMap<>();
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