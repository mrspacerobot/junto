package ch.junto.fixtures;

import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import ch.junto.user.enums.UserStatus;
import java.util.UUID;

public final class UserFixture {

  private UserFixture() {}

  /** Returns a user WITH a generated UUID (for Unit Tests) */
  public static User defaultUser() {
    User user = newUser("user@junto.app");
    user.setId(UUID.randomUUID());
    return user;
  }

  /** Returns a user WITH a generated UUID and specific email (for Unit Tests) */
  public static User userWithEmail(String email) {
    User user = newUser(email);
    user.setId(UUID.randomUUID());
    return user;
  }

  /** Returns a user WITHOUT a UUID (for Integration Tests / DatabaseHelper) */
  public static User newUser(String email) {
    User user = new User();
    user.setEmail(email);
    user.setDisplayName(email.split("@")[0]);
    user.setRole(UserRole.MEMBER);
    user.setStatus(UserStatus.ACTIVE);
    user.setOnboarded(true);
    user.setInviteCodesRemaining(5);
    return user;
  }

  public static User newMember(String email) {
    return newUser(email);
  }

  public static User newAdmin(String email) {
    User user = newUser(email);
    user.setRole(UserRole.ADMIN);
    return user;
  }

  public static User newSuspended(String email) {
    User user = newUser(email);
    user.setStatus(UserStatus.SUSPENDED);
    return user;
  }

  public static User persistableWithUniqueEmail(String prefix) {
    return newUser(prefix + "-" + UUID.randomUUID() + "@junto.app");
  }

  public static User moderator() {
    User user = newUser("mod@junto.app");
    user.setId(UUID.randomUUID());
    user.setRole(UserRole.MODERATOR);
    return user;
  }

  public static User suspended() {
    User user = newUser("suspended@junto.app");
    user.setId(UUID.randomUUID());
    user.setStatus(UserStatus.SUSPENDED);
    return user;
  }

  public static User notOnboarded() {
    User user = newUser("new@junto.app");
    user.setId(UUID.randomUUID());
    user.setOnboarded(false);
    return user;
  }
}
