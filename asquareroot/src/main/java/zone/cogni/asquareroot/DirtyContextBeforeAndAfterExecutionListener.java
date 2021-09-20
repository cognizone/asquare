package zone.cogni.asquareroot;

import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

class DirtyContextBeforeAndAfterExecutionListener extends AbstractTestExecutionListener {

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public void beforeTestClass(TestContext testContext) {
    testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
    EmbeddedContext.get().resetContext(testContext);
    EmbeddedContext.get().setEmbeddedElastic(testContext.getTestClass().isAnnotationPresent(EnableEmbeddedElastic.class));
    EmbeddedContext.get().setRelaxedSelect(testContext.getTestClass().isAnnotationPresent(EnableRelaxedVirtuosoSelectSimulation.class));
    EmbeddedContext.get().setRealEnvironment(testContext.getTestClass().isAnnotationPresent(EnableRealEnvironment.class));
  }

  @Override
  public void afterTestExecution(TestContext testContext) {
    EmbeddedContext.get().unregisterEmbeddedContext();
  }

  @Override
  public void afterTestMethod(TestContext testContext) {
    testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.EXHAUSTIVE);
  }

}
