package ch.junto.auth;

/** Serializes replacement of the current authentication token for one normalized email. */
public interface AuthTokenIssuanceLock {
    void acquire(String normalizedEmail);
}
