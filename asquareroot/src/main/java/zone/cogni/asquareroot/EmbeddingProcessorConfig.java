package zone.cogni.asquareroot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
class EmbeddingProcessorConfig implements BeanPostProcessor, ApplicationContextAware {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingProcessorConfig.class);

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    EmbeddedContext context = EmbeddedContext.get();
    if (context == null) {
      return bean;
    }
    if (context.getRealEnvironment()) {
      return bean;
    }
    if (context.getTestInstance() != null && context.getTestInstance() instanceof EmbeddedEnvironment) {
      return ((EmbeddedEnvironment) context.getTestInstance()).embedBefore(context, bean, beanName);
    }
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    EmbeddedContext context = EmbeddedContext.get();
    if (context == null) {
      return bean;
    }
    if (context.getRealEnvironment()) {
      return bean;
    }
    if (context.getTestInstance() != null && context.getTestInstance() instanceof EmbeddedEnvironment) {
      return ((EmbeddedEnvironment) context.getTestInstance()).embedAfter(context, bean, beanName);
    }
    return bean;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    EmbeddedContext context = EmbeddedContext.get();
    if (context != null && !context.getRealEnvironment()) {
      try {
        EmbeddedContext.get().getRootTempFolder().create();
      }
      catch (IOException e) {
        log.error("Can not create temp directory {}", e.getMessage());
      }
    }
  }
}