package ch.junto.auth;

import ch.junto.shared.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RequestCodeRequest(@NotBlank @Email String email, String inviteCode) {}
    public record VerifyCodeRequest(@NotBlank @Email String email, @NotBlank String code) {}
    public record VerifyTokenRequest(@NotBlank String token) {}

    @PostMapping("/request-code")
    public void requestCode(@RequestBody @Valid RequestCodeRequest request) {
        authService.requestCode(request.email(), request.inviteCode());
    }

    @PostMapping("/verify")
    public void verify(@RequestBody @Valid VerifyCodeRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        User user = authService.verifyCode(request.email(), request.code());
        authenticateUser(user, servletRequest, response);
    }

    @PostMapping("/verify-token")
    public void verifyToken(@RequestBody @Valid VerifyTokenRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        User user = authService.verifyToken(request.token());
        authenticateUser(user, servletRequest, response);
        // Redirect to frontend or home? For now just authenticate.
        // Usually, magic links redirect to a "logged in" page.
    }

    @GetMapping("/me")
    public User me(@CurrentUser User user) {
        return user;
    }

    private void authenticateUser(User user, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, java.util.List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
