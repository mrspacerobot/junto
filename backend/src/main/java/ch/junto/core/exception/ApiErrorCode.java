package ch.junto.core.exception;

/** Stable error identifiers serialized at the HTTP API boundary. */
public enum ApiErrorCode {
  EMAIL_DELIVERY_FAILED("EMAIL_DELIVERY_FAILED");

  private final String wireValue;

  ApiErrorCode(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }
}
