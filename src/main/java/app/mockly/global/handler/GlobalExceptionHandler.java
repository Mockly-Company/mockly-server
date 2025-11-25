package app.mockly.global.handler;

import app.mockly.global.common.ApiStatusCode;
import app.mockly.global.common.ApiResponse;
import app.mockly.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        if (e instanceof InsufficientAuthenticationException) {
            ApiStatusCode statusCode = ApiStatusCode.TOKEN_REQUIRED;
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(statusCode.getCode(), statusCode.getMessage()));
        }
        ApiStatusCode statusCode = ApiStatusCode.UNAUTHORIZED;
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(statusCode.getCode(), statusCode.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ApiStatusCode statusCode = e.getStatusCode();
        return ResponseEntity
                .status(statusCode.getStatus())
                .body(ApiResponse.error(statusCode.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(ApiStatusCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(
                        ApiStatusCode.VALIDATION_ERROR.getCode(),
                        errorMessage.isEmpty() ? ApiStatusCode.VALIDATION_ERROR.getMessage() : errorMessage
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(ApiStatusCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(
                        ApiStatusCode.INTERNAL_SERVER_ERROR.getCode(),
                        ApiStatusCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }
}
