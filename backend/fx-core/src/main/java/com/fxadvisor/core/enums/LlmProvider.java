package com.fxadvisor.core.enums;

/**
 * Supported LLM backend providers.
 *
 * OLLAMA     → local Ollama server at localhost:11434 — free, no API key needed
 *              Set: fx.llm.provider=ollama
 *              Requires: ollama installed + model pulled (e.g. qwen2.5:7b)
 *
 * OPENAI     → api.openai.com — paid, best quality, native tool calling
 *              Set: fx.llm.provider=openai + OPENAI_API_KEY env var
 *
 * ANTHROPIC  → api.anthropic.com via OpenAI-compatible endpoint — paid
 *              Set: fx.llm.provider=anthropic + ANTHROPIC_API_KEY env var
 *              Note: Uses OpenAiChatModel pointed at Anthropic's base URL
 *
 * TOOL CALLING STRATEGY:
 * - OPENAI / ANTHROPIC: native JSON tool-calling protocol via Spring AI .tools()
 *   The LLM receives a JSON schema of available tools and returns structured
 *   tool_calls JSON. Spring AI handles invocation automatically. True streaming.
 *
 * - OLLAMA: simulated ReAct loop using structured XML tags in the prompt.
 *   Most quantized local models (7B–13B) do not reliably produce the OpenAI
 *   tool-calling JSON format (30-50% failure rate). The simulated loop avoids
 *   this by using plain text tags that smaller models handle well:
 *   <tool>fetchExchangeRate</tool><args>{"src":"USD","tgt":"INR"}</args>
 *   The agent parses these, invokes Java methods directly, feeds results back,
 *   and repeats until the model produces a final answer with no tool tags.
 *
 * INTERVIEW: Why is native tool calling unreliable in small local models?
 * Native tool calling requires the model to produce perfectly valid JSON
 * with exact field names matching the schema. Small quantized models are
 * less deterministic — they produce slight variations in field names, add
 * extra whitespace, or omit required fields, causing JSON parse failures.
 * XML tags are more forgiving: the regex match is lenient and the model
 * has fewer constraints on surrounding text.
 */
public enum LlmProvider {

    OLLAMA,
    OPENAI,
    ANTHROPIC;

    /**
     * Returns true if this provider reliably supports the native OpenAI
     * tool-calling JSON protocol through Spring AI's .tools() API.
     *
     * When false, RateAgent uses the simulated ReAct loop instead.
     */
    public boolean supportsNativeToolCalling() {
        return this == OPENAI || this == ANTHROPIC;
    }
}