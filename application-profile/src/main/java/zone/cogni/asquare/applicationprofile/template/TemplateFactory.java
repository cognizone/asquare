package zone.cogni.asquare.applicationprofile.template;

import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Configuration
public class TemplateFactory {

  @Bean
  public TemplateEngine getTemplateEngine() {
    TemplateEngine templateEngine = new TemplateEngine();
    StringTemplateResolver templateResolver = new StringTemplateResolver();
    templateResolver.setTemplateMode(TemplateMode.TEXT);
    templateEngine.setTemplateResolver(templateResolver);

    return templateEngine;
  }

  public InputStreamSource process(@Nonnull InputStreamSource template,
                                   @Nonnull Map<String, Object> replacements) {
    try {
      String templateString = IOUtils.toString(template.getInputStream(), "UTF-8");
      IContext context = new Context(Locale.ENGLISH, replacements); // TODO huh?!?

      String process = getTemplateEngine().process(templateString, context);
      return new ByteArrayResource(process.getBytes("UTF-8"));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
