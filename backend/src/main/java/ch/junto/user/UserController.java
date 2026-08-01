package ch.junto.user;

import ch.junto.auth.CurrentUser;
import ch.junto.auth.dto.MeResponse;
import ch.junto.user.entity.User;
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

    public record UpdateLocationRequest(
        @jakarta.validation.constraints.NotNull Double lat,
        @jakarta.validation.constraints.NotNull Double lng
    ) {}

    @PostMapping("/onboard")
    public MeResponse onboard(@RequestBody @Valid OnboardRequest request, @CurrentUser User user) {
        return MeResponse.from(userService.completeOnboarding(user.getId(), request.displayName()));
    }

    @GetMapping("/profile")
    public MeResponse getProfile(@CurrentUser User user) {
        return MeResponse.from(user);
    }

    @PutMapping("/location")
    public MeResponse updateLocation(@RequestBody @Valid UpdateLocationRequest request, @CurrentUser User user) {
        if (request.lat() < -90 || request.lat() > 90) {
            throw new ch.junto.core.exception.ApiException("VALIDATION_ERROR", "lat must be between -90 and 90", 400);
        }
        if (request.lng() < -180 || request.lng() > 180) {
            throw new ch.junto.core.exception.ApiException("VALIDATION_ERROR", "lng must be between -180 and 180", 400);
        }
        return MeResponse.from(userService.updateLocation(user.getId(), request.lat(), request.lng()));
    }

    @DeleteMapping("/location")
    public MeResponse clearLocation(@CurrentUser User user) {
        return MeResponse.from(userService.clearLocation(user.getId()));
    }

    @DeleteMapping("/me")
    public void deleteAccount(@CurrentUser User user, jakarta.servlet.http.HttpSession session) {
        userService.anonymizeUser(user.getId());
        session.invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
