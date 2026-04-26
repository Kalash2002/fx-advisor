package com.fxadvisor.rate.agent;

import com.fxadvisor.core.domain.CorridorQuote;
import com.fxadvisor.core.enums.LlmProvider;
import com.fxadvisor.rate.tools.RateFetchTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-agnostic ReAct agent for FX rate analysis.
 *
 * PUBLIC API: analyseTransfer(...) → Flux<String>
 * Callers (RateTestController in Sprint 5, AdvisorController in Sprint 9) never
 * need to know which LLM is active. They always get a Flux<String> of tokens.
 *
 * ─── TWO EXECUTION PATHS ──────────────────────────────────────────────────────
 *
 * PATH 1 — NATIVE TOOL CALLING (OpenAI / Anthropic)
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ chatClient.prompt()                                                     │
 * │   .system(systemPrompt)    ← persona + compliance + history            │
 * │   .user(userPrompt)        ← transfer details                          │
 * │   .tools(rateFetchTools)   ← @Tool annotations registered as JSON schema│
 * │   .stream().content()      ← Flux<String> of real tokens               │
 * │                                                                         │
 * │ Spring AI intercepts tool_calls JSON from the LLM response,            │
 * │ invokes the Java methods, feeds results back, and continues streaming.  │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * PATH 2 — SIMULATED REACT LOOP (Ollama / local models)
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Loop (max 8 iterations):                                                │
 * │   1. chatClient.prompt()...call().content() → blocking String response  │
 * │   2. Regex scan for: <tool>name</tool><args>{...}</args>                │
 * │   3. If found: invokeToolByName() dispatches to Java method directly    │
 * │   4. Append response + tool results to message list, loop again         │
 * │   5. If no tool tags found: emit final answer as token stream           │
 * │                                                                         │
 * │ Result: same Flux<String> interface but tokens are the final answer     │
 * │ split into ~4-char chunks (simulated streaming feel).                   │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * INTERVIEW: Why XML tags for Ollama tool calls instead of JSON?
 * JSON requires precise syntax — a missing comma or extra space breaks parsing.
 * Quantized 7B models produce imprecise JSON ~30-50% of the time.
 * XML-style tags are far more lenient: the regex captures content between
 * known delimiters regardless of surrounding whitespace. The model has one
 * simple rule: put the tool name between <tool> tags and args between <args>
 * tags. This dramatically reduces parse failures.
 *
 * INTERVIEW: What is the tradeoff with the simulated loop?
 * The simulated loop uses blocking .call() instead of streaming .stream().
 * The full LLM response is assembled in memory before any tokens are emitted.
 * This means: no true token-by-token streaming with Ollama — the user sees
 * a pause then the full response appears as a "stream" of 4-char chunks.
 * Acceptable during development. Native streaming is restored when switching
 * to OpenAI. This tradeoff is explicitly called out in the UI in Sprint 10.
 */
@Service
public class RateAgent {

    private static final Logger log = LoggerFactory.getLogger(RateAgent.class);

    /**
     * Regex to detect simulated tool calls.
     * Matches: <tool>toolName</tool><args>{"key":"value"}</args>
     * DOTALL flag allows args JSON to span multiple lines.
     */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool>(\\w+)</tool>\\s*<args>(\\{.*?\\})</args>",
            Pattern.DOTALL);

    private final ChatClient chatClient;
    private final RateFetchTools rateFetchTools;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;

    public RateAgent(ChatClient chatClient,
                     RateFetchTools rateFetchTools,
                     LlmProvider llmProvider) {
        this.chatClient = chatClient;
        this.rateFetchTools = rateFetchTools;
        this.llmProvider = llmProvider;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyses a cross-border transfer and returns a streaming recommendation.
     *
     * @param sourceCurrency      ISO 4217 source currency, e.g. "USD"
     * @param targetCurrency      ISO 4217 target currency, e.g. "INR"
     * @param amount              Transfer amount as string, e.g. "1000.00"
     * @param urgency             Delivery urgency: INSTANT, SAME_DAY, or STANDARD
     * @param complianceContext   RAG-retrieved RBI/FEMA text (empty in Sprint 5, wired in Sprint 6)
     * @param conversationHistory Previous session messages (empty in Sprint 5, wired in Sprint 8)
     * @return Flux<String> of tokens — subscribers receive the LLM's streamed output
     */
    public Flux<String> analyseTransfer(
            String sourceCurrency,
            String targetCurrency,
            String amount,
            String urgency,
            String complianceContext,
            String conversationHistory) {

        log.debug("analyseTransfer called: {}/{} amount={} urgency={} provider={}",
                sourceCurrency, targetCurrency, amount, urgency, llmProvider);

        if (llmProvider.supportsNativeToolCalling()) {
            return analyseWithNativeTools(sourceCurrency, targetCurrency,
                    amount, urgency, complianceContext, conversationHistory);
        } else {
            return analyseWithSimulatedReact(sourceCurrency, targetCurrency,
                    amount, urgency, complianceContext, conversationHistory);
        }
    }

    // ─── Path 1: Native Tool Calling (OpenAI / Anthropic) ────────────────────

    private Flux<String> analyseWithNativeTools(
            String src, String tgt, String amount, String urgency,
            String compliance, String history) {

        log.debug("Using native tool calling path (provider: {})", llmProvider);

        return chatClient.prompt()
                .system(buildSystemPrompt(compliance, history, false))
                .user(buildUserPrompt(src, tgt, amount, urgency))
                .tools(rateFetchTools)
                .stream()
                .content();
    }

    // ─── Path 2: Simulated ReAct Loop (Ollama / local models) ─────────────────

    /**
     * Runs the manual ReAct loop for models without reliable native tool calling.
     *
     * Loop breakdown:
     * - Iteration 1: model reads the request and calls fetchExchangeRate
     * - Iteration 2: model receives the rate and calls calculateEffectiveRate for SWIFT
     * - Iterations 3-5: model calls calculateEffectiveRate for remaining corridors
     * - Final iteration: model produces the recommendation with no tool tags
     *
     * Max 8 iterations — protects against infinite loops if the model is confused.
     * In practice, 6 iterations cover the full flow for 4 corridors.
     */

    // Commenting this approach as olama is hallucinating, and do not calling the tools, also runs only one iteration
    // fix is for now in local to simulated runs and then for production run on api key for more accuracy
    private Flux<String> analyseWithSimulatedReact(
            String src, String tgt, String amount, String urgency,
            String compliance, String history) {

        log.debug("Using simulated ReAct loop (provider: {})", llmProvider);

        return Flux.create(emitter -> {
            try {
                String systemPrompt = buildSystemPrompt(compliance, history, true);
                List<Message> messages = new ArrayList<>();
                messages.add(new UserMessage(buildUserPrompt(src, tgt, amount, urgency)));

                String finalResponse = "";
                int maxIterations = 8;

                for (int i = 0; i < maxIterations; i++) {
                    log.debug("Simulated ReAct iteration {} / {}", i + 1, maxIterations);

                    // Blocking call — full response assembled in memory
                    String response = chatClient.prompt()
                            .system(systemPrompt)
                            .messages(messages)
                            .call()
                            .content();

                    if (response == null || response.isBlank()) {
                        log.warn("Empty response from LLM at iteration {}", i + 1);
                        break;
                    }

                    finalResponse = response;

                    // Look for tool calls in the response
                    Matcher matcher = TOOL_CALL_PATTERN.matcher(response);
                    if (!matcher.find()) {
                        // No tool calls found — this is the final answer
                        log.debug("No tool calls found — final answer ready at iteration {}", i + 1);
                        break;
                    }

                    // Process all tool calls found in this response
                    StringBuilder toolResultsBlock = new StringBuilder();
                    matcher.reset();
                    int toolCallCount = 0;

                    while (matcher.find()) {
                        String toolName = matcher.group(1).trim();
                        String argsJson = matcher.group(2).trim();

                        log.debug("Dispatching tool call: {} with args: {}", toolName, argsJson);
                        String result = invokeToolByName(toolName, argsJson);

                        toolResultsBlock.append("<tool_result for=\"")
                                .append(toolName).append("\">\n")
                                .append(result).append("\n</tool_result>\n");
                        toolCallCount++;
                    }

                    log.debug("Processed {} tool call(s) in iteration {}", toolCallCount, i + 1);

                    // Append model response and tool results to conversation history
                    messages.add(new AssistantMessage(response));
                    messages.add(new UserMessage(
                            "Here are the tool results:\n\n" + toolResultsBlock
                                    + "\nContinue your analysis. Call more tools if needed. "
                                    + "When you have all corridor data, provide the final recommendation "
                                    + "WITHOUT any tool tags."));
                }

                // Emit the final response as a simulated token stream
                // Split into ~4-char chunks to mimic real streaming behaviour
                List<String> tokens = splitIntoTokens(finalResponse);
                log.debug("Emitting {} token chunks for final response", tokens.size());

                for (String token : tokens) {
                    emitter.next(token);
                }
                emitter.complete();

            } catch (Exception e) {
                log.error("Simulated ReAct loop failed: {}", e.getMessage(), e);
                emitter.error(e);
            }
        });
    }

    // ─── Tool Dispatch (used only by simulated ReAct path) ───────────────────

    /**
     * Manually dispatches a tool call by name with JSON-encoded arguments.
     *
     * This replaces Spring AI's automatic dispatch which is only available
     * on the native tool-calling path. We parse the args JSON, extract the
     * relevant parameters, and invoke the corresponding RateFetchTools method.
     *
     * Argument key names are flexible because different model runs may use
     * slightly different field names (src vs sourceCurrency, rate vs midMarketRate).
     * We check both variants with getOrDefault to handle this gracefully.
     *
     * @param toolName  Name of the tool to invoke (must match @Tool method names)
     * @param argsJson  JSON string with tool arguments from model output
     * @return String representation of the tool result (rate number or JSON quote)
     */
    @SuppressWarnings("unchecked")
    private String invokeToolByName(String toolName, String argsJson) {
        try {
            Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);

            return switch (toolName) {
                case "fetchExchangeRate" -> {
                    // Accept both short form (src/tgt) and long form (sourceCurrency/targetCurrency)
                    String src = (String) args.getOrDefault("src",
                            args.getOrDefault("sourceCurrency", "USD"));
                    String tgt = (String) args.getOrDefault("tgt",
                            args.getOrDefault("targetCurrency", "INR"));

                    BigDecimal rate = rateFetchTools.fetchExchangeRate(src, tgt);
                    log.debug("fetchExchangeRate({},{}) = {}", src, tgt, rate);
                    yield rate.toPlainString();
                }

                case "calculateEffectiveRate" -> {
                    // Accept both rate and midMarketRate as the rate key
                    Object rateRaw = args.getOrDefault("midMarketRate",
                            args.getOrDefault("rate", "83.0"));
                    BigDecimal rate = new BigDecimal(rateRaw.toString());

                    String corridor = (String) args.getOrDefault("corridorType",
                            args.getOrDefault("corridor", "UPI"));
                    Object amountRaw = args.getOrDefault("amount", "1000");
                    BigDecimal amount = new BigDecimal(amountRaw.toString());
                    String urgency = (String) args.getOrDefault("urgency", "STANDARD");

                    CorridorQuote quote = rateFetchTools.calculateEffectiveRate(
                            rate, corridor, amount, urgency);
                    String resultJson = objectMapper.writeValueAsString(quote);
                    log.debug("calculateEffectiveRate({}) = {}", corridor, resultJson);
                    yield resultJson;
                }

                default -> {
                    log.warn("Unknown tool requested by model: '{}'", toolName);
                    yield "Unknown tool: " + toolName
                            + ". Available tools: fetchExchangeRate, calculateEffectiveRate";
                }
            };

        } catch (Exception e) {
            log.error("Tool dispatch failed for '{}' with args '{}': {}",
                    toolName, argsJson, e.getMessage());
            return "Error invoking tool '" + toolName + "': " + e.getMessage()
                    + ". Please retry with correct argument format.";
        }
    }

    // ─── Prompt Builders ──────────────────────────────────────────────────────

    /**
     * Builds the system prompt with optional simulated tool-call instructions.
     *
     * @param complianceContext  RAG text — empty in Sprint 5, populated in Sprint 6
     * @param conversationHistory  Redis history — empty in Sprint 5, populated in Sprint 8
     * @param simulatedMode  When true, includes the XML tag tool-call format instructions
     */
    private String buildSystemPrompt(String complianceContext,
                                     String conversationHistory,
                                     boolean simulatedMode) {

        String basePersona = """
                You are an expert FX rate advisor helping users make smart cross-border transfer decisions.
                
                        CRITICAL RULES — YOU MUST FOLLOW THESE:
                        1. ALWAYS call the fetchExchangeRate tool to get the live rate. NEVER guess the rate.
                        2. The example rate of 83.42 in the tool description is OUTDATED — the real rate is different today.
                        3. ALWAYS call calculateEffectiveRate for ALL four corridors: SWIFT, UPI, CRYPTO_USDT, WISE.
                        4. Only recommend after you have REAL tool results, not assumed values.
                        5. If you do not call the tools, your answer will be WRONG.
                """;

        String toolInstructions;
        if (simulatedMode) {
            toolInstructions = """
                    
                    ── TOOL CALLING FORMAT ──────────────────────────────────────────
                    When you need to call a tool, output EXACTLY this format (no other text on the same lines):
                    <tool>TOOL_NAME</tool><args>{"key":"value"}</args>
                    
                    AVAILABLE TOOLS:
                    
                    Tool: fetchExchangeRate
                    Purpose: Get current mid-market exchange rate from live API
                    Args:    {"src": "<SOURCE_CURRENCY>", "tgt": "<TARGET_CURRENCY>"}
                    Example: <tool>fetchExchangeRate</tool><args>{"src":"USD","tgt":"INR"}</args>
                    
                    Tool: calculateEffectiveRate
                    Purpose: Compute received amount for one corridor after applying spread and fees
                    Args:    {"midMarketRate": <NUMBER>, "corridorType": "<CORRIDOR>", "amount": <NUMBER>, "urgency": "<URGENCY>"}
                    Valid corridorType values: SWIFT, UPI, CRYPTO_USDT, WISE
                    Valid urgency values:      INSTANT, SAME_DAY, STANDARD
                    Example: <tool>calculateEffectiveRate</tool><args>{"midMarketRate":83.42,"corridorType":"UPI","amount":1000,"urgency":"STANDARD"}</args>
                    
                    ── MANDATORY ANALYSIS SEQUENCE ──────────────────────────────────
                    Step 1: Call fetchExchangeRate to get the live rate
                    Step 2: Call calculateEffectiveRate for SWIFT
                    Step 3: Call calculateEffectiveRate for UPI
                    Step 4: Call calculateEffectiveRate for CRYPTO_USDT
                    Step 5: Call calculateEffectiveRate for WISE
                    Step 6: Write your final recommendation — NO tool tags in the final answer
                    
                    IMPORTANT: In your final answer, do NOT include any <tool> or <args> tags.
                    """;
        } else {
            toolInstructions = """
                    
                    MANDATORY RULES:
                    - ALWAYS use tools to fetch rates — never guess or use historical rates
                    - ALWAYS compute quotes for all four corridors before recommending
                    - Show your step-by-step reasoning
                    """;
        }

        String outputFormat = """
                
                OUTPUT FORMAT FOR FINAL ANSWER:
                1. Live Rate: [mid-market rate fetched]
                2. Corridor Comparison (markdown table with: corridor, effective rate, fees, received amount, delivery, eligible)
                3. Recommendation: [best corridor with explanation]
                4. Compliance Notes: [any regulatory flags, or "None" if clear]
                """;

        String complianceSection = complianceContext.isBlank()
                ? "\nCOMPLIANCE CONTEXT: Not available for this request.\n"
                : "\nCOMPLIANCE CONTEXT (RBI/FEMA regulations):\n" + complianceContext + "\n";

        String historySection = conversationHistory.isBlank()
                ? ""
                : "\nCONVERSATION HISTORY:\n" + conversationHistory + "\n";

        return basePersona + toolInstructions + outputFormat + complianceSection + historySection;
    }

    private String buildUserPrompt(String src, String tgt, String amount, String urgency) {
        return """
                Please analyse this cross-border transfer:
                
                Amount:   %s %s → %s
                Urgency:  %s
                
                Fetch the live rate, calculate quotes for all four corridors (SWIFT, UPI, CRYPTO_USDT, WISE),
                and recommend the best option. Show mid-market rate, effective rate, total fees, received
                amount, and estimated delivery time for each corridor.
                """.formatted(amount, src, tgt, urgency);
    }

    /**
     * Splits a complete response string into small token-like chunks.
     * Used to simulate streaming in the Ollama path where the full response
     * is assembled before any output is emitted.
     *
     * Chunk size of 4 characters approximates the average token length
     * in real LLM streaming responses (a "token" in BPE encoding is
     * roughly 3-5 characters on average).
     */
    private List<String> splitIntoTokens(String text) {
        List<String> tokens = new ArrayList<>();
        int chunkSize = 4;
        for (int i = 0; i < text.length(); i += chunkSize) {
            tokens.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return tokens;
    }
}
