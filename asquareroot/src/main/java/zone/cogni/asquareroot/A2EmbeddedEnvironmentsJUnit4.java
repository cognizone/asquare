package zone.cogni.asquareroot;

import org.junit.ClassRule;
import org.junit.Rule;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

@Import(EmbeddingProcessorConfig.class)
@TestExecutionListeners(
  listeners = DirtyContextBeforeAndAfterExecutionListener.class,
  mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
public class A2EmbeddedEnvironmentsJUnit4 implements EmbeddedEnvironment {

  @ClassRule
  public static final SpringClassRule scr = new SpringClassRule();

  @Rule
  public final SpringMethodRule smr = new SpringMethodRule();
}
