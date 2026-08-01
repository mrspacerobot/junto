package ch.junto.auth;

import ch.junto.auth.dto.MeResponse;
import ch.junto.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter authRateLimiter;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService, AuthRateLimiter authRateLimiter) {
        this.authService = authService;
        this.authRateLimiter = authRateLimiter;
    }

    public record RequestCodeRequest(@NotBlank @Email String email, String inviteCode) {}
    public record VerifyCodeRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String code
    ) {}
    public record VerifyTokenRequest(@NotBlank String token) {}

    @PostMapping("/request-code")
    public void requestCode(
        @RequestBody @Valid RequestCodeRequest request,
        HttpServletRequest servletRequest
    ) {
        authRateLimiter.checkCodeRequest(request.email(), servletRequest.getRemoteAddr());
        authService.requestCode(request.email(), request.inviteCode());
    }

    @PostMapping("/verify")
    public void verify(@RequestBody @Valid VerifyCodeRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        authRateLimiter.checkCodeVerification(request.email(), servletRequest.getRemoteAddr());
        User user = authService.verifyCode(request.email(), request.code());
        authenticateUser(user, servletRequest, response);
    }

    @PostMapping("/verify-token")
    public void verifyToken(@RequestBody @Valid VerifyTokenRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        authRateLimiter.checkTokenVerification(request.token(), servletRequest.getRemoteAddr());
        User user = authService.verifyToken(request.token());
        authenticateUser(user, servletRequest, response);
        // Redirect to frontend or home? For now just authenticate.
        // Usually, magic links redirect to a "logged in" page.
    }

    @GetMapping("/me")
    public MeResponse me(@CurrentUser User user) {
        return MeResponse.from(authService.refreshUser(user.getId()));
    }

    private void authenticateUser(User user, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            new SessionPrincipal(user.getId()), null, java.util.List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
