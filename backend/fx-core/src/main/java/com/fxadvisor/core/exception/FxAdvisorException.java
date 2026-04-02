package com.fxadvisor.core.exception;

/**
 * Base exception for all application-level errors.
 *
 * The errorCode field is a machine-readable string used by GlobalExceptionHandler
 * to map exceptions to HTTP status codes without if-instanceof chains.
 *
 * Convention: errorCode = "DOMAIN_DESCRIPTION"
 * Examples: "AUTH_INVALID_TOKEN", "RATE_FETCH_FAILED", "COMPLIANCE_RETRIEVAL_ERROR"
 */
public class FxAdvisorException extends RuntimeException {

    private final String errorCode;

    public FxAdvisorException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FxAdvisorException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "FxAdvisorException{errorCode='" + errorCode + "', message='" + getMessage() + "'}";
    }
}