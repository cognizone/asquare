package zone.cogni.asquareroot;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;

@Import(EmbeddingProcessorConfig.class)
@TestExecutionListeners(
  listeners = DirtyContextBeforeAndAfterExecutionListener.class,
  mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
public class A2EmbeddedEnvironmentsJUnit5 implements EmbeddedEnvironment{

}
