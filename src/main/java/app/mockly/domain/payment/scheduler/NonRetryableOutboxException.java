package app.mockly.domain.payment.scheduler;

public class NonRetryableOutboxException extends RuntimeException {

    private final String failureCode;

    public NonRetryableOutboxException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public NonRetryableOutboxException(String failureCode, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
