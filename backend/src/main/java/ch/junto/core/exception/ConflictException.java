package ch.junto.core.exception;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super("CONFLICT", message, 409);
    }

    public ConflictException(String code, String message, int status) {
        super(code, message, status);
    }
}
