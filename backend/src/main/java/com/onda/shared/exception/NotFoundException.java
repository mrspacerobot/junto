package com.onda.shared.exception;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message, 404);
    }

    public NotFoundException(String code, String message, int status) {
        super(code, message, status);
    }
}
