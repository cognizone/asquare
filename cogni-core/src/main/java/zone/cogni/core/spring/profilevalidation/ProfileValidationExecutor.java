package zone.cogni.core.spring.profilevalidation;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProfileValidationExecutor {
  private static final Logger log = LoggerFactory.getLogger(ProfileValidationExecutor.class);

  private final Environment environment;
  private final DefaultListableBeanFactory registry;

  public ProfileValidationExecutor(Environment environment, ApplicationContext applicationContext) {
    this.environment = environment;
    registry = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    ;
  }

  @EventListener
  public void handleContextRefresh(ContextRefreshedEvent event) {
    log.info("Checking profiles...");
    String[] beanNames = registry.getBeanNamesForAnnotation(ProfileValidation.class);
    Arrays.stream(beanNames).map(beanName -> registry.findAnnotationOnBean(beanName, ProfileValidation.class)).forEach(this::handle);
  }

  private void handle(@Nullable ProfileValidation profileValidation) {
    if (null == profileValidation) throw new RuntimeException("Asked for beans with ProfileValidation annotation, but then it's not there... woezel");
    Arrays.stream(profileValidation.disjoint()).forEach(this::check);
  }

  private void check(Disjoint disjoint) {

    log.info("Disjoint {} - mandatory: {}", disjoint.value(), disjoint.mandatory());

    Set<String> securityProfiles = Sets.newHashSet(disjoint.value());
    List<String> activeSecurityProfiles = Arrays.stream(environment.getActiveProfiles())
                                                .filter(securityProfiles::contains)
                                                .collect(Collectors.toList());

    if (activeSecurityProfiles.size() > 1) {
      throw new RuntimeException("Not 1 disjoint profile: list " + securityProfiles + " -  found " + activeSecurityProfiles + " - all active " + Arrays.toString(environment.getActiveProfiles()));
    }
    if (disjoint.mandatory() && activeSecurityProfiles.size() != 1) {
      throw new RuntimeException("Not exactly 1 disjoint profile: list " + securityProfiles + " - found " + activeSecurityProfiles + " - all active " + Arrays.toString(environment.getActiveProfiles()));
    }

  }

}
