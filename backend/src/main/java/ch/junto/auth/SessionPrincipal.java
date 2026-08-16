package ch.junto.auth;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * Minimal value persisted in Spring Session. JPA entities and package-specific lazy proxies must
 * never be serialized into the JDBC session store.
 */
public record SessionPrincipal(UUID userId) implements Principal, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  public SessionPrincipal {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
  }

  @Override
  public String getName() {
    return userId.toString();
  }
}
