package ch.junto.auth.dto;

import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import ch.junto.user.enums.UserStatus;
import java.util.UUID;

public record MeResponse(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    String bio,
    String city,
    Double locationLat,
    Double locationLng,
    UserRole role,
    UserStatus status,
    boolean onboarded,
    int inviteCodesRemaining) {
  public static MeResponse from(User user) {
    return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getAvatarUrl(),
        user.getBio(),
        user.getCity(),
        user.getLocationLat(),
        user.getLocationLng(),
        user.getRole(),
        user.getStatus(),
        user.isOnboarded(),
        user.getInviteCodesRemaining());
  }
}
