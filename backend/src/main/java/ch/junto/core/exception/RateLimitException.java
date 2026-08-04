package ch.junto.core.exception;

public class RateLimitException extends ApiException {

  public RateLimitException(String message) {
    super("RATE_LIMITED", message, 429);
  }
}
