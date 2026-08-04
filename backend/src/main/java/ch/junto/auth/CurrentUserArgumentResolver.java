package ch.junto.auth;

import ch.junto.core.exception.ForbiddenException;
import ch.junto.user.UserService;
import ch.junto.user.entity.User;
import ch.junto.user.enums.UserStatus;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  private final UserService userService;

  public CurrentUserArgumentResolver(UserService userService) {
    this.userService = userService;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentUser.class)
        && parameter.getParameterType().equals(User.class);
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
      return null;
    }

    User user = userService.getById(principal.userId());
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new ForbiddenException("Account is not active");
    }
    return user;
  }
}
