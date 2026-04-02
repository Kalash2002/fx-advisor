package com.fxadvisor.core.exception;

/**
 * Thrown when the Frankfurter API call fails.
 *
 * Causes:
 * - HTTP 5xx from Frankfurter
 * - Connection timeout (> 5s)
 * - Response body malformed (rate not parseable as BigDecimal)
 * - Unsupported currency pair (Frankfurter returns empty rates map)
 *
 * Handling in GlobalExceptionHandler: maps to HTTP 502 BAD_GATEWAY
 * (upstream dependency failure, not a client error)
 *
 * SSE behavior: streams an error event to the client so the browser
 * can display an error toast instead of hanging forever.
 */
public class RateFetchException extends FxAdvisorException {

    public RateFetchException(String message) {
        super("RATE_FETCH_FAILED", message);
    }

    public RateFetchException(String message, Throwable cause) {
        super("RATE_FETCH_FAILED", message, cause);
    }
}