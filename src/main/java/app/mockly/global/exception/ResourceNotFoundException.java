package app.mockly.global.exception;

import app.mockly.global.common.ApiStatusCode;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(ApiStatusCode statusCode) {
        super(statusCode);
    }

    public ResourceNotFoundException(ApiStatusCode statusCode, String message) {
        super(statusCode, message);
    }
}
