package ch.junto.fixtures;

import ch.junto.auth.entity.AuthToken;
import ch.junto.helpers.TimeTestHelper;
import java.util.UUID;

public final class AuthTokenFixture {

  private AuthTokenFixture() {}

  public static AuthToken valid(String email) {
    AuthToken token = new AuthToken();
    token.setId(UUID.randomUUID());
    token.setEmail(email);
    token.setToken(UUID.randomUUID());
    token.setCode("123456");
    token.setExpiresAt(TimeTestHelper.hoursFromNow(1));
    return token;
  }

  public static AuthToken expired(String email) {
    AuthToken token = new AuthToken();
    token.setId(UUID.randomUUID());
    token.setEmail(email);
    token.setToken(UUID.randomUUID());
    token.setCode("654321");
    token.setExpiresAt(TimeTestHelper.hoursAgo(1));
    return token;
  }

  public static AuthToken used(String email) {
    AuthToken token = valid(email);
    token.setUsedAt(TimeTestHelper.hoursAgo(1));
    return token;
  }

  public static AuthToken withInviteCode(String email, String inviteCode) {
    AuthToken token = valid(email);
    token.setInviteCode(inviteCode);
    return token;
  }
}
