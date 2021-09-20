package zone.cogni.asquare.security.permission;

import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.method.MethodSecurityMetadataSource;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;
import zone.cogni.asquare.security.service.PermissionService;

import java.lang.annotation.Annotation;
import java.util.function.Function;

public abstract class PermissionTypeSecurityConfiguration<PERMISSION_TYPE extends Enum<PERMISSION_TYPE>, ANNOTATION_TYPE extends Annotation> extends GlobalMethodSecurityConfiguration {
  private final Class<ANNOTATION_TYPE> annotationClass;
  private final Function<ANNOTATION_TYPE, PERMISSION_TYPE[]> getPermissionsFromAnnotation;
  private final Function<ANNOTATION_TYPE, PermissionStrategy> getPermissionStrategy;

  public PermissionTypeSecurityConfiguration(Class<ANNOTATION_TYPE> annotationClass,
                                             Function<ANNOTATION_TYPE, PERMISSION_TYPE[]> getPermissionsFromAnnotation,
                                             Function<ANNOTATION_TYPE, PermissionStrategy> getPermissionStrategy) {
    this.annotationClass = annotationClass;
    this.getPermissionsFromAnnotation = getPermissionsFromAnnotation;
    this.getPermissionStrategy = getPermissionStrategy;
  }

  public abstract PermissionService<PERMISSION_TYPE> getPermissionService();

  @Override
  protected MethodSecurityMetadataSource customMethodSecurityMetadataSource() {
    return new PermissionMethodSecurityMetadataSource<>(annotationClass, getPermissionsFromAnnotation, getPermissionStrategy);
  }

  @Override
  protected AccessDecisionManager accessDecisionManager() {
    AffirmativeBased manager = (AffirmativeBased) super.accessDecisionManager();
    manager.getDecisionVoters().add(new PermissionAccessDecisionVoter<>(getPermissionService()));
    return manager;
  }

}
