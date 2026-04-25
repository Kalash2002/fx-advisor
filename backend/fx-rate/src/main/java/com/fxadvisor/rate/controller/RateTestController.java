package com.fxadvisor.rate.controller;

import com.fxadvisor.rate.agent.RateAgent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;

/**
 * DEV-ONLY endpoint for testing the RateAgent SSE stream.
 *
 * No authentication, no session logic, no audit logging.
 * Exists solely to validate that:
 * 1. The selected LLM provider is configured correctly
 * 2. FrankfurterClient reaches api.frankfurter.app
 * 3. The ReAct loop (native or simulated) produces corridor quotes
 * 4. Tokens stream to the client over SSE
 *
 * REMOVED IN SPRINT 9 — replaced by AdvisorController which adds:
 * @PreAuthorize("hasAuthority('PERMISSION_ANALYSE')")
 * Redis session history, async audit logging, compliance context from pgvector.
 *
 * INTERVIEW: Why SSE instead of WebSocket for streaming LLM output?
 * SSE is unidirectional (server → client) — exactly what LLM streaming needs.
 * WebSockets are bidirectional which adds complexity with no benefit here.
 * SSE works natively with HTTP/1.1 chunked transfer encoding, EventSource in
 * browsers, and curl --no-buffer on the command line. Simpler to implement,
 * debug, and proxy through infrastructure (nginx, load balancers).
 */
@RestController
@RequestMapping("/api/v1/rate")
public class RateTestController {

    private final RateAgent rateAgent;

    public RateTestController(RateAgent rateAgent) {
        this.rateAgent = rateAgent;
    }

    /**
     * POST /api/v1/rate/test
     *
     * Streams a live FX rate analysis for the given transfer parameters.
     * Response Content-Type: text/event-stream
     *
     * Test with curl (shows tokens arriving in real time):
     * curl -X POST http://localhost:8080/api/v1/rate/test \
     *   -H "Content-Type: application/json" \
     *   -d '{"sourceCurrency":"USD","targetCurrency":"INR","amount":"1000","urgency":"STANDARD"}' \
     *   --no-buffer
     *
     * With Ollama: expect a pause (model thinking), then tokens flood in.
     * With OpenAI: expect tokens to arrive one-by-one in real time.
     */
    @PostMapping(
            value = "/test",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> testRateStream(
            @Valid @RequestBody RateTestRequest request) {

        return rateAgent.analyseTransfer(
                        request.sourceCurrency(),
                        request.targetCurrency(),
                        request.amount().toPlainString(),
                        request.urgency(),
                        "",   // compliance context — wired in Sprint 6
                        ""    // conversation history — wired in Sprint 8
                )
                .map(token -> ServerSentEvent.<String>builder()
                        .event("token")
                        .data(token)
                        .build())
                .onErrorReturn(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data("Rate analysis failed. Check LLM provider config and Frankfurter connectivity.")
                                .build()
                );
    }

    /**
     * Request DTO for the test endpoint.
     * Replaced by TransferRequest from fx-core in Sprint 9.
     */
    public record RateTestRequest(
            @NotBlank String sourceCurrency,
            @NotBlank String targetCurrency,
            @Positive BigDecimal amount,
            @NotBlank String urgency
    ) {}
}