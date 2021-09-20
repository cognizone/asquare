package zone.cogni.asquareroot.security;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import zone.cogni.asquare.security.model.AuthenticationToken;

public class WithMockAsquareUserSecurityContextFactory implements WithSecurityContextFactory<WithMockAsquareUser> {
  @Override
  public SecurityContext createSecurityContext(WithMockAsquareUser asquareUser) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();

    AuthenticationToken ivUserAuthToken = new AuthenticationToken(asquareUser.ivUser());
    ivUserAuthToken.setFullName(asquareUser.fullName());

    context.setAuthentication(ivUserAuthToken);
    return context;
  }
}
