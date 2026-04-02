package com.fxadvisor.core.exception;

/**
 * Thrown when the pgvector RAG retrieval fails.
 *
 * Causes:
 * - Postgres connection failure
 * - pgvector extension not installed
 * - Embedding model call failure (OpenAI API down during query embedding)
 * - Empty vector store (no documents ingested yet)
 *
 * Handling in CorridorOrchestratorAgent: caught and SWALLOWED.
 * The orchestrator continues in degraded mode — the AI analysis proceeds
 * without compliance context rather than failing entirely.
 * A warning note is prepended to the prompt: "Compliance data unavailable."
 *
 * This is intentional fault tolerance: a rate lookup still has value
 * even if compliance context is temporarily unavailable.
 */
public class ComplianceException extends FxAdvisorException {

    public ComplianceException(String message) {
        super("COMPLIANCE_RETRIEVAL_ERROR", message);
    }

    public ComplianceException(String message, Throwable cause) {
        super("COMPLIANCE_RETRIEVAL_ERROR", message, cause);
    }
}