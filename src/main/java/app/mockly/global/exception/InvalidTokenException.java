package app.mockly.global.exception;

import app.mockly.global.common.ApiStatusCode;

public class InvalidTokenException extends BusinessException {
    public InvalidTokenException(ApiStatusCode statusCode) {
        super(statusCode);
    }

    public InvalidTokenException(ApiStatusCode statusCode, String message) {
        super(statusCode, message);
    }

    public InvalidTokenException(ApiStatusCode statusCode, String message, Throwable cause) {
        super(statusCode, message, cause);
    }
}
