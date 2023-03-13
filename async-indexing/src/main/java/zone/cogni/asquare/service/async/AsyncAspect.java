package zone.cogni.asquare.service.async;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Aspect
public class AsyncAspect {

  private static final Logger log = LoggerFactory.getLogger(AsyncAspect.class);

  @Around("@annotation(org.springframework.scheduling.annotation.Async)")
  public Object logAction(ProceedingJoinPoint joinPoint) throws Throwable {
    final CompletableFuture<Object> cfuture = AsyncTaskManager.getCompletableFuture();
    if (cfuture == null) {
      return joinPoint.proceed();
    }

    try {
      Object result = joinPoint.proceed();
      cfuture.complete(result);
      return result;
    }
    catch (Exception ex) {
      cfuture.completeExceptionally(ex);
      log.error("Async method executed with an exception: {}", ex);
    }
    return null;
  }

}
