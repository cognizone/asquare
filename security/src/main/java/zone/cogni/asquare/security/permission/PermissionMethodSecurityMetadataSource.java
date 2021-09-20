package zone.cogni.asquare.security.permission;

import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.method.AbstractMethodSecurityMetadataSource;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

class PermissionMethodSecurityMetadataSource<PERMISSION_TYPE extends Enum<PERMISSION_TYPE>, ANNOTATION_TYPE extends Annotation> extends AbstractMethodSecurityMetadataSource {
  private final Class<ANNOTATION_TYPE> annotationClass;
  private final Function<ANNOTATION_TYPE, PERMISSION_TYPE[]> getPermissionsFromAnnotation;
  private final Function<ANNOTATION_TYPE, PermissionStrategy> getPermissionStrategy;

  public PermissionMethodSecurityMetadataSource(Class<ANNOTATION_TYPE> annotationClass,
                                                Function<ANNOTATION_TYPE, PERMISSION_TYPE[]> getPermissionsFromAnnotation,
                                                Function<ANNOTATION_TYPE, PermissionStrategy> getPermissionStrategy) {
    this.annotationClass = annotationClass;
    this.getPermissionsFromAnnotation = getPermissionsFromAnnotation;
    this.getPermissionStrategy = getPermissionStrategy;
  }

  @Override
  public Collection<ConfigAttribute> getAttributes(Method method, Class<?> targetClass) {
    ANNOTATION_TYPE hasPermissionAnnotation = method.getAnnotation(annotationClass);
    if (null == hasPermissionAnnotation) return Collections.emptyList();

    PERMISSION_TYPE[] permissions = getPermissionsFromAnnotation.apply(hasPermissionAnnotation);
    PermissionStrategy permissionStrategy = getPermissionStrategy.apply(hasPermissionAnnotation);
    return Collections.singletonList(new PermissionConfigAttribute<>(permissions, permissionStrategy));
  }

  @Override
  public Collection<ConfigAttribute> getAllConfigAttributes() {
    return Collections.emptyList();
  }
}
