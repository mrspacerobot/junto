package ch.junto.auth;

import ch.junto.user.UserService;
import ch.junto.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * Dev-only endpoint to log in as any existing user by email.
 * Only active when the "dev" profile is set.
 */
@RestController
@RequestMapping("/api/auth")
@Profile("dev")
public class DevAuthController {

    private final UserService userService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public DevAuthController(UserService userService) {
        this.userService = userService;
    }

    public record DevLoginRequest(@NotBlank String email) {}

    @PostMapping("/dev-login")
    public void devLogin(@RequestBody @Valid DevLoginRequest body, HttpServletRequest servletRequest, HttpServletResponse response) {
        String email = body.email().trim().toLowerCase();
        User user = userService.findByEmail(email)
            .orElseGet(() -> userService.createFromEmail(email));
        if (!user.isOnboarded()) {
            user = userService.completeOnboarding(user.getId(), email.substring(0, email.indexOf('@')));
        }

        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            new SessionPrincipal(user.getId()), null, java.util.List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, response);
    }
}
