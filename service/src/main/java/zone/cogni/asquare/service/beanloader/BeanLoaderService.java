package zone.cogni.asquare.service.beanloader;

import com.google.common.base.Preconditions;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import zone.cogni.asquare.access.ApplicationView;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import java.util.Objects;

/**
 * Until we have a better idea of how to implement this we should not continue development.
 */
@Deprecated
public class BeanLoaderService implements ApplicationContextAware {

  private ApplicationContext applicationContext;
  private DefaultListableBeanFactory beanFactory;

  @PostConstruct
  public void init() {
    beanFactory = new DefaultListableBeanFactory(applicationContext);
  }

  @Override
  public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  public Object get(ApplicationView applicationView, String typeId, String uri) {
    return new BeanRegistry(beanFactory, applicationView, typeId, uri).get();
  }

  public <T> T get(ApplicationView applicationView, String typeId, String uri, Class<T> clazz) {
    Object instance = get(applicationView, typeId, uri);

    Preconditions.checkState(Objects.equals(instance.getClass(), clazz));
    return (T) instance;
  }
}
