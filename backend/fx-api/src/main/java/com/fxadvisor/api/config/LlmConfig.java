package com.fxadvisor.api.config;

import com.fxadvisor.core.enums.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects and configures the primary ChatClient based on fx.llm.provider.
 *
 * WHY a manual bean instead of letting Spring AI auto-configure one?
 * When both spring-ai-openai-spring-boot-starter and
 * spring-ai-ollama-spring-boot-starter are on the classpath, Spring AI
 * auto-configures BOTH ChatModel beans. Without @Primary guidance, Spring
 * cannot determine which one to inject into RateAgent, causing a
 * NoUniqueBeanDefinitionException at startup.
 *
 * This class solves that by:
 * 1. Injecting both auto-configured ChatModel beans
 * 2. Reading fx.llm.provider to decide which one to use
 * 3. Wrapping the selected model in a ChatClient and marking it @Primary
 *
 * Result: RateAgent injects ChatClient (interface) — never touches
 * OpenAiChatModel or OllamaChatModel directly. Provider-agnostic.
 *
 * SWITCHING PROVIDERS:
 * Change fx.llm.provider in application.yml (or FX_LLM_PROVIDER env var).
 * Restart the app. No code changes required anywhere.
 *
 * INTERVIEW: What design pattern is this?
 * Strategy Pattern + Factory. LlmConfig is the factory that selects the
 * correct strategy (ChatModel implementation) based on configuration.
 * RateAgent is the context — it calls the strategy without knowing which one.
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);


    /**
     * Primary ChatClient bean injected throughout the application.
     *
     * @Primary ensures this bean wins over any ChatClient candidates
     * that Spring AI auto-configuration might register.
     *
     * Both openAiChatModel and ollamaChatModel are auto-configured
     * by their respective Spring AI starters. We accept both here
     * and select one based on the provider property.
     */

    @Value("${fx.llm.provider:openai}")
    private String providerName;

    @Bean
    @Primary
    public ChatClient primaryChatClient(OpenAiChatModel openAiChatModel) {
        LlmProvider provider = parseProvider();
        log.info("=== LLM Provider: {} | Native tool calling: {} ===",
                provider, provider.supportsNativeToolCalling());
        // Groq uses OPENAI provider type (OpenAI-compatible protocol)
        return ChatClient.builder(openAiChatModel).build();
    }
//    @Bean
//    @Primary
//    public ChatClient primaryChatClient(
//            OpenAiChatModel openAiChatModel,
//            OllamaChatModel ollamaChatModel) {
//
//        LlmProvider provider = parseProvider();
//        log.info("=== LLM Provider: {} | Native tool calling: {} ===",
//                provider, provider.supportsNativeToolCalling());
//
//        return switch (provider) {
//            // OPENAI and ANTHROPIC both use the OpenAiChatModel.
//            // For ANTHROPIC, the base-url in application.yml points to
//            // Anthropic's OpenAI-compatible endpoint instead of api.openai.com
//            case OPENAI, ANTHROPIC -> ChatClient.builder(openAiChatModel).build();
//            case OLLAMA             -> ChatClient.builder(ollamaChatModel).build();
//        };
//    }

//    @Bean
//    @Primary
//    public ChatClient primaryChatClient(OllamaChatModel ollamaChatModel) {
//        LlmProvider provider = parseProvider();
//        log.info("=== LLM Provider: {} | Native tool calling: {} ===",
//                provider, provider.supportsNativeToolCalling());
//        // Only Ollama for now — add OpenAI branch when you have an API key
//        return ChatClient.builder(ollamaChatModel).build();
//    }

    /**
     * Exposes the active LlmProvider as a bean.
     * RateAgent injects this to decide which execution strategy to use.
     */
    @Bean
    public LlmProvider llmProvider() {
        LlmProvider provider = parseProvider();
        log.info("LlmProvider bean registered: {}", provider);
        return provider;
    }

    private LlmProvider parseProvider() {
        try {
            return LlmProvider.valueOf(providerName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown LLM provider '{}', defaulting to OLLAMA", providerName);
            return LlmProvider.OLLAMA;
        }
    }
}