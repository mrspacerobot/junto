package ch.junto.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.junto.base.BaseIntegrationTest;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The security property behind "bootstrap can never be triggered through a public endpoint": the
 * running application does not contain the bootstrap at all, so there is nothing for a request,
 * authenticated or not, to reach.
 */
@DisplayName("Bootstrap over HTTP")
class BootstrapNotReachableOverHttpTest extends BaseIntegrationTest {

  private static final List<String> GUESSABLE_PATHS =
      List.of(
          "/api/bootstrap",
          "/api/bootstrap-admin",
          "/api/admin/bootstrap",
          "/api/auth/bootstrap",
          "/bootstrap",
          "/bootstrap-admin");

  @Autowired private ApplicationContext context;
  @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("is not part of the running application")
  void isNotABeanOfTheRunningApplication() {
    assertThat(context.getBeanNamesForType(BootstrapAdminService.class)).isEmpty();
    assertThat(
            Arrays.stream(context.getBeanDefinitionNames())
                .map(context::getType)
                .filter(type -> type != null)
                .map(Class::getPackageName)
                .filter(packageName -> packageName.startsWith("ch.junto.bootstrap")))
        .isEmpty();
  }

  @Test
  @DisplayName("has no request mapping, on any path or from any package")
  void hasNoRequestMapping() {
    requestMappingHandlerMapping
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              assertThat(handler.getBeanType().getPackageName())
                  .doesNotStartWith("ch.junto.bootstrap");
              assertThat(mapping.toString().toLowerCase()).doesNotContain("bootstrap");
            });
  }

  @Test
  @DisplayName("answers no bootstrap path, anonymously or signed in")
  void answersNoBootstrapPath() throws Exception {
    User member = userRepository.save(UserFixture.newMember("bootstrap-probe@junto.app"));
    MockHttpSession session = MockMvcTestHelper.sessionFor(member);

    for (String path : GUESSABLE_PATHS) {
      assertRejected(mockMvc.perform(get(path)).andReturn());
      assertRejected(mockMvc.perform(post(path)).andReturn());
      assertRejected(mockMvc.perform(get(path).session(session)).andReturn());
      assertRejected(mockMvc.perform(post(path).session(session)).andReturn());
    }
  }

  private static void assertRejected(MvcResult result) {
    assertThat(result.getResponse().getStatus())
        .as(result.getRequest().getMethod() + " " + result.getRequest().getRequestURI())
        .isIn(401, 403, 404, 405);
  }
}
