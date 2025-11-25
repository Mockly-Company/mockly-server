package app.mockly.global.exception;

import app.mockly.global.common.ApiStatusCode;

public class InvalidTokenException extends BusinessException {
    public InvalidTokenException() {
        super(ApiStatusCode.INVALID_TOKEN);
    }

    public InvalidTokenException(String message) {
        super(ApiStatusCode.INVALID_TOKEN, message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(ApiStatusCode.INVALID_TOKEN, message, cause);
    }
}
