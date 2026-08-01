package ch.junto.core.exception;

public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }

    public ForbiddenException(String code, String message, int status) {
        super(code, message, status);
    }
}
