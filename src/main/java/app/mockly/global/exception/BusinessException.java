package app.mockly.global.exception;

import app.mockly.global.common.ApiStatusCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ApiStatusCode statusCode;

    public BusinessException(ApiStatusCode statusCode) {
        super(statusCode.getMessage());
        this.statusCode = statusCode;
    }

    public BusinessException(ApiStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public BusinessException(ApiStatusCode statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
