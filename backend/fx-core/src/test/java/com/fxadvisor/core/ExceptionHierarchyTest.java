package com.fxadvisor.core;

import com.fxadvisor.core.exception.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {

    @Test
    void allExceptionsExtendFxAdvisorException() {
        assertTrue(FxAdvisorException.class.getSuperclass() == RuntimeException.class);
        assertTrue(RateFetchException.class.getSuperclass() == FxAdvisorException.class);
        assertTrue(ComplianceException.class.getSuperclass() == FxAdvisorException.class);
        assertTrue(AuthException.class.getSuperclass() == FxAdvisorException.class);
    }

    @Test
    void rateFetchExceptionHasCorrectErrorCode() {
        RateFetchException ex = new RateFetchException("API timeout");
        assertEquals("RATE_FETCH_FAILED", ex.getErrorCode());
        assertEquals("API timeout", ex.getMessage());
    }

    @Test
    void complianceExceptionHasCorrectErrorCode() {
        ComplianceException ex = new ComplianceException("pgvector unavailable");
        assertEquals("COMPLIANCE_RETRIEVAL_ERROR", ex.getErrorCode());
    }

    @Test
    void authExceptionHasCorrectErrorCode() {
        AuthException ex = new AuthException("Invalid credentials");
        assertEquals("AUTH_ERROR", ex.getErrorCode());
    }

    @Test
    void rateFetchExceptionPreservesCause() {
        Throwable cause = new RuntimeException("Connection refused");
        RateFetchException ex = new RateFetchException("Frankfurter unreachable", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void allExceptionsAreUnchecked() {
        // Verify no custom exception requires a throws declaration
        assertInstanceOf(RuntimeException.class, new RateFetchException("test"));
        assertInstanceOf(RuntimeException.class, new ComplianceException("test"));
        assertInstanceOf(RuntimeException.class, new AuthException("test"));
    }
}