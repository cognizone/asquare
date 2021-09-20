package zone.cogni.asquare.applicationprofile.template;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = TemplateFactory.class)
class TemplateFactoryTest {

  @Autowired
  TemplateFactory templateFactory;

  @Test
  void template_test() throws IOException {
    // given
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("name", "Homer");

    String templateString = "Name : [[${name}]]";
    InputStreamSource templateResource = new ByteArrayResource(templateString.getBytes("UTF-8"));

    // when
    InputStreamSource applicationProfileResource = templateFactory.process(templateResource, parameters);

    // then
    String result = IOUtils.toString(applicationProfileResource.getInputStream(), "UTF-8");
    assertEquals("Name : Homer", result);
  }
}
