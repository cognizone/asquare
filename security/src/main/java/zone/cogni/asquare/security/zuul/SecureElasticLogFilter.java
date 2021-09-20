package zone.cogni.asquare.security.zuul;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import zone.cogni.asquare.security.service.PermissionService;

import java.util.Arrays;
import java.util.List;

@Component
public class SecureElasticLogFilter<T extends Enum> extends ZuulFilter {

  private final PermissionService<T> permissionService;

  private final List<String> protectedIndexes;
  private final String protectedRoute;
  private final String[] permissionAllow;

  public SecureElasticLogFilter(
      @Value("${cognizone.elastic.security.protected:keyword-en, userlog, keyword-fr, data, ldapinfo, taxonomy, keyword-it}")
        String[] protectedIndexes,
      @Value("${cognizone.elastic.security.protected-route:elasticsearch}")
        String protectedRoute,
      @Value("${cognizone.elastic.security.filterAllowRole:ADMIN}")
        String[] permissionAllow,
      PermissionService<T> permissionService) {
    this.permissionService = permissionService;
    this.protectedIndexes = Arrays.asList(protectedIndexes);
    this.protectedRoute = protectedRoute;
    this.permissionAllow = permissionAllow;
  }

  @Override
  public String filterType() {
    return FilterConstants.POST_TYPE;
  }

  @Override
  public int filterOrder() {
    return FilterConstants.PRE_DECORATION_FILTER_ORDER + 1;
  }

  @Override
  public boolean shouldFilter() {
    RequestContext ctx = RequestContext.getCurrentContext();
    Object proxy = ctx.get("proxy");

    return protectedRoute.equals(proxy);
  }

  @Override
  public Object run() {
    RequestContext ctx = RequestContext.getCurrentContext();
    String index = ctx.getRequest().getParameter("index");
    if (StringUtils.isBlank(index)) {
      ctx.unset();
      ctx.setResponseStatusCode(HttpStatus.UNAUTHORIZED.value());
      return null;
    }
    else if (protectedIndexes.contains(index)) {
      boolean hasPermission = permissionService.hasAnyPermission(SecurityContextHolder.getContext().getAuthentication(), permissionAllow);
      if (!hasPermission) {
        ctx.unset();
        ctx.setResponseStatusCode(HttpStatus.UNAUTHORIZED.value());
        return null;
      }
    }
    return null;
  }
}
