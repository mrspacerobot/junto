package ch.junto.auth.dto;

import ch.junto.shared.entity.User;
import ch.junto.shared.enums.UserRole;
import ch.junto.shared.enums.UserStatus;

import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    String bio,
    String city,
    UserRole role,
    UserStatus status,
    boolean onboarded,
    int inviteCodesRemaining
) {
    public static MeResponse from(User user) {
        return new MeResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getCity(),
            user.getRole(),
            user.getStatus(),
            user.isOnboarded(),
            user.getInviteCodesRemaining()
        );
    }
}
