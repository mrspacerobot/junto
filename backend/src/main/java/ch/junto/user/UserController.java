package ch.junto.user;

import ch.junto.auth.CurrentUser;
import ch.junto.auth.dto.MeResponse;
import ch.junto.shared.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record OnboardRequest(@NotBlank String displayName) {}

    @PostMapping("/onboard")
    public MeResponse onboard(@RequestBody @Valid OnboardRequest request, @CurrentUser User user) {
        return MeResponse.from(userService.completeOnboarding(user.getId(), request.displayName()));
    }

    @GetMapping("/profile")
    public MeResponse getProfile(@CurrentUser User user) {
        return MeResponse.from(user);
    }

    @DeleteMapping("/me")
    public void deleteAccount(@CurrentUser User user, jakarta.servlet.http.HttpSession session) {
        userService.anonymizeUser(user.getId());
        session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
