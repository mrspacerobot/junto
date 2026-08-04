package ch.junto.core.exception;

public class ApiException extends RuntimeException {

    private final String code;
    private final int status;

    public ApiException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ApiException(ApiErrorCode code, String message, int status) {
        this(code.wireValue(), message, status);
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
