package zone.cogni.asquareroot.elastic;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import zone.cogni.asquareroot.A2EmbeddedEnvironmentsJUnit5;
import zone.cogni.asquareroot.EnableEmbeddedElastic;
import zone.cogni.asquareroot.testapp.TestApplication;
import zone.cogni.asquareroot.testapp.TestApplicationConfig;

@EnableEmbeddedElastic
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TestApplication.class)
public class ApplicationTest extends A2EmbeddedEnvironmentsJUnit5 {

  @Autowired
  public TestApplicationConfig testConfig;

  @Test
  public void test() {
    Assertions.assertNotNull(testConfig);
  }
}
