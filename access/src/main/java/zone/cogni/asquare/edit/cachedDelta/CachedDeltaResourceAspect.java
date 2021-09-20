package zone.cogni.asquare.edit.cachedDelta;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zone.cogni.asquare.FeatureFlag;
import zone.cogni.asquare.access.AccessType;
import zone.cogni.asquare.access.ApplicationView;
import zone.cogni.asquare.applicationprofile.model.basic.ApplicationProfile;
import zone.cogni.asquare.edit.DeltaResource;
import zone.cogni.asquare.edit.MutableResource;
import zone.cogni.asquare.rdf.RdfValue;
import zone.cogni.asquare.rdf.TypedResource;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Aspect
@Component
public class CachedDeltaResourceAspect {

  private static final ThreadLocal<Map<ApplicationView, DeltaResourceCache>> cachedDeltaResources = new ThreadLocal<>();

  private static DeltaResourceCache getCache(ApplicationView view) {
    cacheMustBeInitialized();
    return cachedDeltaResources.get().get(view);
  }

  public static boolean isCacheInitialized() {
    return cachedDeltaResources.get() != null;
  }

  private static void cacheMustBeInitialized() {
    if (!isCacheInitialized()) throw new RuntimeException("The DeltaResource cache hasn't been initialized");
  }

  public static List<DeltaResource> getAllCachedResources(ApplicationView view) {
    return getCache(view).getAllResources();
  }

  public static boolean isResourceCached(ApplicationView view, String resourceUri, String typeId) {
    return isResourceCached(view, resourceUri, view.getApplicationProfile().getType(typeId));
  }

  public static boolean isResourceCached(ApplicationView view, TypedResource resource) {
    return isResourceCached(view, resource.getResource().getURI(), resource.getType());
  }

  public static boolean isResourceCached(ApplicationView view, String resourceUri, ApplicationProfile.Type type) {
    return getCache(view).hasResource(resourceUri, type);
  }

  public static DeltaResource find(ApplicationView view, String resourceUri, String typeId) {
    return find(view, resourceUri, view.getApplicationProfile().getType(typeId));
  }

  public static DeltaResource find(ApplicationView view, TypedResource resource) {
    return find(view, resource.getResource().getURI(), resource.getType());
  }

  public static DeltaResource find(ApplicationView view, String resourceUri, ApplicationProfile.Type type) {
    return getCache(view).getResource(resourceUri, type);
  }

  public static <T extends RdfValue> T get(ApplicationView view, T resource) {
    if (!(resource instanceof TypedResource)) {
      return resource;
    }
    return (T)view.getDeltaResource(((TypedResource) resource)::getType, resource.getResource().getURI());
  }

  public static void cacheResource(ApplicationView view, DeltaResource deltaResource) {
    getCache(view).cacheResource(deltaResource);
  }

  public static void saveCache(ApplicationView view) {
    view.save(getCache(view).getAllResources());
  }

  // todo : RuntimeException if no applicationView ?
  @Around("@annotation(CachedDeltaResource)")
  public Object cacheDeltaResource(ProceedingJoinPoint joinPoint) throws Throwable {
    if (!FeatureFlag.cachedDeltaResource) return joinPoint.proceed();

    ApplicationView view = getApplicationView(joinPoint);
    if (view == null) return joinPoint.proceed();
    if (view.getRepository().getAccessType() != getAccessType(joinPoint)) {
      throw new IllegalStateException("The method " + getMethod(joinPoint).getName() +
                                      " annotation states that it use an " + getAccessType(joinPoint).toString() +
                                      " AccessService, but the AccessService " + view.getRepository().getClass().getName() +
                                      " is set as an " + view.getRepository().getAccessType().toString() +
                                      " AccessService.");
    }
    boolean isViewAlreadyCached = isCacheInitialized() && getCache(view) != null;
    try {
      initializeCache(view, joinPoint);
      return joinPoint.proceed();
    } finally {
      if (!isViewAlreadyCached) {
        cachedDeltaResources.get().remove(view);
        if (cachedDeltaResources.get().isEmpty()) {
          cachedDeltaResources.remove();
        }
      }
    }
  }

  private void initializeCache(ApplicationView view, ProceedingJoinPoint joinPoint) {
    if (!isCacheInitialized()) {
      Map<ApplicationView, DeltaResourceCache> cache = new HashMap<>();
      cachedDeltaResources.set(cache);
    }
    if (getCache(view) == null) {
      cachedDeltaResources.get().put(view, new DeltaResourceCache());
    }
    getJsonResource(joinPoint).ifPresent(mutableResource -> {
      if (getAccessType(joinPoint) == AccessType.ELASTIC) {
        view.createNewDeltaResource(mutableResource);
      } else {    // default case : AccessType.RDF
        cacheResourceInDepth(view, mutableResource);
      }
    });
  }

  private static void cacheResourceInDepth(ApplicationView view, MutableResource mutableResource) {
    mutableResource.getType().getAttributes().values().stream()
            .filter(mutableResource::hasAttribute)
            .map(mutableResource::getValues)
            .flatMap(Collection::stream)
            .filter(value -> value instanceof TypedResource)
            .forEach(resource -> cacheResourceInDepth(view, (MutableResource)resource));
    view.setDeltaResource(mutableResource);
  }

  @Nullable
  private ApplicationView getApplicationView(ProceedingJoinPoint joinPoint) {
    return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof ApplicationView)
            .map(arg -> (ApplicationView)arg)
            .findFirst()
            .orElse(null);
  }

  private Optional<MutableResource> getJsonResource(ProceedingJoinPoint joinPoint) {
    return Arrays.stream(joinPoint.getArgs())
            .filter(arg -> arg instanceof MutableResource)
            .map(arg -> (MutableResource)arg)
            .findFirst();
  }

  private AccessType getAccessType(ProceedingJoinPoint joinPoint) {
    return getMethod(joinPoint).getAnnotation(CachedDeltaResource.class).accessType();
  }

  private Method getMethod(ProceedingJoinPoint joinPoint) {
    MethodSignature methodSignature = (MethodSignature)joinPoint.getSignature();
    return methodSignature.getMethod();
  }
}
