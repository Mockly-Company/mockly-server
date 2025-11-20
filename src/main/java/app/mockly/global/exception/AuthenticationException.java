package app.mockly.global.exception;

import app.mockly.global.common.ApiStatusCode;

public class AuthenticationException extends BusinessException {
    public AuthenticationException(ApiStatusCode statusCode) {
        super(statusCode);
    }

    public AuthenticationException(ApiStatusCode statusCode, String message) {
        super(statusCode, message);
    }

    public AuthenticationException(ApiStatusCode statusCode, String message, Throwable cause) {
        super(statusCode, message, cause);
    }
}
