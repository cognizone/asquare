package zone.cogni.asquare.service.async;


import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncAnnotationAdvisor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class AsyncUtilsTest {

  final CountDownLatch latch = new CountDownLatch(1);
  private ExecutorService pool;

  @BeforeEach
  public void prepare() {
    pool = Executors.newFixedThreadPool(3);
  }

  @Test
  public void testRunnable() throws InterruptedException {
    for (int i = 0; i < 5; i++) {
      pool.submit(new Runnable() {
        public void run() {
          try {
            latch.await();
          }
          catch (InterruptedException consumeAndExit) {
            System.out.println(Thread.currentThread().getName() + " was interrupted - exiting");
          }
        }
      });
    }

    // the Runnables returned from shutdownNow are not
    // the same objects as those submitted to the pool
    List<Runnable> tasks = pool.shutdownNow();

    for (Runnable task : tasks) {
      Object realTask = AsyncUtils.findRealTask(task);
      assertTrue(realTask instanceof Runnable);
    }
  }

  @Test
  public void testCallable() {
    for (int i = 0; i < 5; i++) {
      pool.submit(new Callable<String>() {
        public String call() throws InterruptedException {
          latch.await();
          return "success";
        }
      });
    }

    List<Runnable> tasks = pool.shutdownNow();

    for (Runnable task : tasks) {
      Object realTask = AsyncUtils.findRealTask(task);
      assertTrue(realTask instanceof Callable);
    }
  }

  @Test
  public void testFindAsyncContextAnnotation() {
    Annotation a = Mockito.mock(Annotation.class);
    Annotation b = Mockito.mock(AsyncContext.class);
    Annotation c = Mockito.mock(Annotation.class);

    Class annotationClass = Annotation.class;
    Class asyncContextClass = AsyncContext.class;

    when(a.annotationType()).thenReturn(annotationClass);
    when(b.annotationType()).thenReturn(asyncContextClass);
    when(c.annotationType()).thenReturn(annotationClass);

    Annotation[] annotations = {a, b, c};
    AsyncContext asyncContext = AsyncUtils.findAsyncContextAnnotation(annotations);

    assertNotNull(asyncContext);
    assertEquals(b, asyncContext);
  }

  @Test
  public void testFindMethodInvocation() {
    TestFindMethodInvocationClass testObj = new TestFindMethodInvocationClass();
    testObj.methodInvocation = Mockito.mock(MethodInvocation.class);

    Field[] fields = TestFindMethodInvocationClass.class.getDeclaredFields();

    assertNotNull(fields);
    assertEquals(1, fields.length);

    Field f = fields[0];
    MethodInvocation mi = AsyncUtils.findMethodInvocation(f, testObj);

    assertNotNull(mi);
    assertEquals(testObj.methodInvocation, mi);
  }

  @Test
  public void testAsyncWithMapForSpringProxy() {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.register(CustomAsyncAnnotationConfig.class, TestService.class);
    ctx.refresh();

    TestService bean = (TestService) ctx.getBean(TestService.class);
    assertTrue(AopUtils.isAopProxy(bean));
    boolean isAsyncAdvised = false;
    for (Advisor advisor : ((Advised) bean).getAdvisors()) {
      if (advisor instanceof AsyncAnnotationAdvisor) {
        isAsyncAdvised = true;
        break;
      }
    }
    assertTrue(isAsyncAdvised, "bean was not async advised as expected");

    AsyncTaskManager executor = (AsyncTaskManager) ctx.getBean(AsyncTaskManager.class);
    AtomicBoolean lock = new AtomicBoolean(true);
    AtomicBoolean timeout = new AtomicBoolean(false);

    bean.testAsync("test value", value -> timeout.set(AsyncUtils.timeoutWhileLock(lock, 10000)));

    assertTrue(executor.isBusy(),
               "Check if executor is executing locked task. Please check if @EnableAsync is declared with (mode = AdviceMode.PROXY)");
    List<String> keys = executor.getExecutionKeysAsStrings();
    assertNotNull(keys,
                  "Check if locked task have correct execution key. Please check if @EnableAsync is declared with (mode = AdviceMode.PROXY)");
    assertEquals(1, keys.size(),
                 "Check if there is single execution task. Please check if @EnableAsync is declared with (mode = AdviceMode.PROXY)");
    assertEquals("test value", keys.get(0),
                 "Check if execution task has correctly calculated execution key. Please check if @EnableAsync is declared with (mode = AdviceMode.PROXY)");
    assertFalse(timeout.get(), "Check if timeout 10 seconds is ok for async logistics operations, otherwise something is wrong.");

    lock.set(false);
    AsyncUtils.timeoutWhile(10000, ()->executor.isBusy());

    lock.set(true);
    bean.testAsync("test value", value -> timeout.set(AsyncUtils.timeoutWhileLock(lock, 10000)));

    CompletableFuture<Object> cf = executor.findCompletableFuture("test value");
    AtomicBoolean isCompletedOfficially = new AtomicBoolean(false);
    cf.whenComplete((a, b) -> {
      isCompletedOfficially.set(true);
    });

    assertTrue(executor.isBusy(),
               "Check if executor is executing locked task. Please check if @EnableAsync is declared with (mode = AdviceMode.PROXY)");

    assertFalse(isCompletedOfficially.get(), "Check if completable future was not completed yet");
    executor.awaitBusyWithNotMoreAndNoLongerThan(0, 10);
    assertTrue(isCompletedOfficially.get(), "Check if completable future was officially completed before forced interruption");

    assertFalse(executor.isBusy(),
                "Check if executor is not executing anything.");

    lock.set(false);
    AsyncUtils.timeoutWhileLock(lock, 10000);

    AsyncUtils.timeoutWhile(1000, ()->true);

    ctx.close();
  }

  private static class TestFindMethodInvocationClass {
    MethodInvocation methodInvocation;
  }

  @EnableAsync(mode = AdviceMode.PROXY)
  static class CustomAsyncAnnotationConfig {
    @Bean("testTaskExecutor")
    public AsyncTaskManager getAsyncTaskExecutor() {
      return new AsyncTaskManager("test executor", map -> map.get("key"));
    }
  }

  @Component
  static class TestService {

    @Async("testTaskExecutor")
    public void testAsync(@AsyncContext("key") String strValue, Consumer<String> someMethod) {
      someMethod.accept("exec");
    }
  }
}
