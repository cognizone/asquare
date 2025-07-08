package zone.cogni.asquare.service.async;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE) // Execute before Spring's async processing
public class AsyncAspect {

  private static final Logger log = LoggerFactory.getLogger(AsyncAspect.class);

  @Around("@annotation(org.springframework.scheduling.annotation.Async)")
  public Object logAction(ProceedingJoinPoint joinPoint) throws Throwable {
    log.debug("AsyncAspect.logAction called for method: {}", joinPoint.getSignature().getName());
    
    // Check if we're already in an async execution context
    final CompletableFuture<Object> cfuture = AsyncTaskManager.getCompletableFuture();
    
    if (cfuture != null) {
      // We're in the async execution thread, handle CompletableFuture
      log.debug("In async execution thread, completing future");
      try {
        Object result = joinPoint.proceed();
        cfuture.complete(result);
        return result;
      }
      catch (Exception ex) {
        cfuture.completeExceptionally(ex);
        log.error("Async method executed with an exception: {}", ex);
        throw ex;
      }
    } else {
      // We're in the calling thread, capture context before async submission
      Map<String, Object> asyncContext = AsyncUtils.getAsyncContext(joinPoint);
      log.debug("In calling thread, captured async context: {}", asyncContext);
      AsyncUtils.setAsyncContext(asyncContext);
      
      try {
        // Proceed with the async method call (will trigger Spring's async processing)
        return joinPoint.proceed();
      } finally {
        // Context will be cleared by the task decorator after it captures it
      }
    }
  }

}
