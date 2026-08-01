package ch.junto.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MockMvc supplies a container MockHttpSession, while Spring Session wraps the
 * request with its repository-backed session. Bridge only the test fixture's
 * SecurityContext so endpoint tests still exercise the real JDBC session path.
 */
@Component
@Profile("test")
@Order(SessionRepositoryFilter.DEFAULT_ORDER + 1)
public class TestMockSessionBridgeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletRequest original = request;
        while (original instanceof HttpServletRequestWrapper wrapper
            && wrapper.getRequest() instanceof HttpServletRequest wrapped) {
            original = wrapped;
        }

        HttpSession mockSession = original.getSession(false);
        if (mockSession != null) {
            Object securityContext = mockSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (securityContext != null) {
                request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext);
            }
        }
        filterChain.doFilter(request, response);
    }
}
