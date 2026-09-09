package app.mockly.global.exception;

import app.mockly.global.common.ApiStatusCode;

public class InvalidClientInfoException extends BusinessException {

    public InvalidClientInfoException() {
        super(ApiStatusCode.INVALID_CLIENT_INFO);
    }
}
