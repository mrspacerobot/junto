package ch.junto.user;

import java.util.Locale;

/**
 * The single definition of an account's normalized email identity.
 *
 * <p>Authentication looks accounts up by this form, so anything that creates an account outside the
 * signup flow - the one-time administrator bootstrap - has to normalize identically or the account
 * it creates could never be signed in to.
 */
public final class EmailNormalizer {

  private EmailNormalizer() {}

  public static String normalize(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
