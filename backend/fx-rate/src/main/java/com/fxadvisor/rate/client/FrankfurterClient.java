package com.fxadvisor.rate.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxadvisor.core.exception.RateFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * HTTP client for the Frankfurter FX rate API.
 *
 * Frankfurter is a free, open-source exchange rate API backed by the European
 * Central Bank reference rates. No API key required. Rates update daily ~16:00 CET.
 * Source: https://api.frankfurter.app
 *
 * Endpoint: GET /latest?from={src}&to={tgt}
 * Response: {"base":"USD","rates":{"INR":83.42},"date":"2025-04-15"}
 *
 * DESIGN: Uses Spring 6 RestClient (not deprecated RestTemplate).
 * RestClient is synchronous — correct for @Tool methods which are invoked
 * synchronously by Spring AI's tool execution engine.
 *
 * WHY NOT WebClient here?
 * @Tool methods must return values immediately (synchronously) so the LLM
 * can observe them in the same ReAct turn. WebClient returns Mono<T> which
 * would need .block() — that causes thread-blocking and potential deadlocks
 * when nested inside a reactive pipeline. RestClient avoids this entirely.
 *
 * ERROR HANDLING:
 * All failure modes throw RateFetchException (maps to HTTP 502 BAD_GATEWAY).
 * - HTTP 4xx/5xx from Frankfurter API
 * - Connection timeout or network failure
 * - Currency pair not found (empty rates map)
 * - Unparseable response body
 */
@Component
public class FrankfurterClient {

    private static final String BASE_URL = "https://api.frankfurter.app";
    private static final Logger log = LoggerFactory.getLogger(FrankfurterClient.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FrankfurterClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "FxAdvisor/1.0")
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetches the current mid-market exchange rate between two currencies.
     *
     * @param sourceCurrency ISO 4217 source currency, e.g. "USD"
     * @param targetCurrency ISO 4217 target currency, e.g. "INR"
     * @return Exchange rate as BigDecimal, e.g. 83.42 for USD→INR
     * @throws RateFetchException if the API call fails or the pair is unsupported
     */
    @SuppressWarnings("unchecked")
    public BigDecimal getRate(String sourceCurrency, String targetCurrency) {
        log.debug("Fetching rate {}/{} from Frankfurter", sourceCurrency, targetCurrency);

        // Step 1: Fetch as raw String — String.class ALWAYS deserializes correctly.
        // Map.class and ParameterizedTypeReference can both silently return null
        // due to type erasure + Jackson version differences in Spring Boot 3.3.
        String rawJson;

//        try {
//            Map<String, Object> response = restClient.get()
//                    .uri("/latest?from={src}&to={tgt}", sourceCurrency, targetCurrency)
//                    .retrieve()
//                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
//
//            if (response == null) {
//                throw new RateFetchException(
//                        "Frankfurter API returned null response for %s/%s"
//                                .formatted(sourceCurrency, targetCurrency));
//            }
//
//            Map<String, Object> rates = (Map<String, Object>) response.get("rates");
//            if (rates == null || !rates.containsKey(targetCurrency)) {
//                throw new RateFetchException(
//                        "No rate found for currency pair %s/%s — unsupported by Frankfurter"
//                                .formatted(sourceCurrency, targetCurrency));
//            }
//
//            Object rateValue = rates.get(targetCurrency);
//
//            // Frankfurter returns numbers as Double in the JSON response.
//            // Convert via BigDecimal.valueOf(double) — preserves decimal precision
//            // better than new BigDecimal(double.toString()) for exchange rate values.
//            if (rateValue instanceof Number number) {
//                return BigDecimal.valueOf(number.doubleValue());
//            }
//
//            throw new RateFetchException(
//                    "Unexpected rate value type for %s/%s: %s"
//                            .formatted(sourceCurrency, targetCurrency,
//                                    rateValue.getClass().getSimpleName()));
//
//        } catch (RestClientException e) {
//            throw new RateFetchException(
//                    "HTTP call to Frankfurter failed for %s/%s: %s"
//                            .formatted(sourceCurrency, targetCurrency, e.getMessage()), e);
//        }

        try {
            rawJson = restClient.get()
                    .uri("/latest?from={src}&to={tgt}", sourceCurrency, targetCurrency)
                    .retrieve()
                    .body(String.class);

            log.debug("Frankfurter raw JSON: {}", rawJson);

        } catch (RestClientException e) {
            throw new RateFetchException(
                    "HTTP call to Frankfurter failed for %s/%s: %s"
                            .formatted(sourceCurrency, targetCurrency, e.getMessage()), e);
        }

        if (rawJson == null || rawJson.isBlank()) {
            throw new RateFetchException(
                    "Frankfurter API returned empty response for %s/%s"
                            .formatted(sourceCurrency, targetCurrency));
        }

        try {
            FrankfurterResponse parsed = objectMapper.readValue(rawJson, FrankfurterResponse.class);

            if (parsed.rates() == null) {
                throw new RateFetchException(
                        "Frankfurter response missing 'rates' field for %s/%s. Raw: %s"
                                .formatted(sourceCurrency, targetCurrency, rawJson));
            }

            BigDecimal rate = parsed.rates().get(targetCurrency);
            if (rate == null) {
                throw new RateFetchException(
                        "No rate for '%s' in Frankfurter response. Available: %s"
                                .formatted(targetCurrency, parsed.rates().keySet()));
            }

            log.info("Fetched rate {}/{} = {}", sourceCurrency, targetCurrency, rate);
            return rate;

        } catch (RateFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new RateFetchException(
                    "Failed to parse Frankfurter response for %s/%s. Raw JSON: [%s]. Error: %s"
                            .formatted(sourceCurrency, targetCurrency, rawJson, e.getMessage()), e);
        }

    }

    private record FrankfurterResponse(
            String base,
            String date,
            Map<String, BigDecimal> rates
    ) {}
}