package com.fxadvisor.rate.client;

import com.fxadvisor.core.exception.RateFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * Fetches the current mid-market exchange rate between two currencies.
     *
     * @param sourceCurrency ISO 4217 source currency, e.g. "USD"
     * @param targetCurrency ISO 4217 target currency, e.g. "INR"
     * @return Exchange rate as BigDecimal, e.g. 83.42 for USD→INR
     * @throws RateFetchException if the API call fails or the pair is unsupported
     */

    public BigDecimal getRate(String sourceCurrency, String targetCurrency) {
        String urlStr = BASE_URL + "/latest?from=" + sourceCurrency + "&to=" + targetCurrency;
        log.info("Calling Frankfurter API: {}", urlStr);

        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            log.info("Frankfurter response code: {}", responseCode);

            if (responseCode != 200) {
                throw new RateFetchException(
                        "Frankfurter API returned HTTP " + responseCode +
                                " for " + sourceCurrency + "/" + targetCurrency);
            }

            String responseBody;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                responseBody = reader.lines().collect(Collectors.joining());
            }

            log.info("Frankfurter raw response: {}", responseBody);

            if (responseBody == null || responseBody.isBlank()) {
                throw new RateFetchException(
                        "Frankfurter API returned empty body for " +
                                sourceCurrency + "/" + targetCurrency);
            }

            return parseRate(responseBody, targetCurrency, sourceCurrency);

        } catch (RateFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new RateFetchException(
                    "Failed to call Frankfurter API for " +
                            sourceCurrency + "/" + targetCurrency + ": " + e.getMessage(), e);
        }
    }

    private BigDecimal parseRate(String json, String targetCurrency, String sourceCurrency) {
        // Look for "INR":94.06 pattern in the rates object
        String searchKey = "\"" + targetCurrency + "\":";
        int keyIndex = json.indexOf(searchKey);

        if (keyIndex == -1) {
            // Check if source == target (Frankfurter returns empty rates for same currency)
            if (sourceCurrency.equalsIgnoreCase(targetCurrency)) {
                throw new RateFetchException(
                        "Source and target currency cannot be the same: " + sourceCurrency);
            }
            throw new RateFetchException(
                    "Currency " + targetCurrency + " not found in Frankfurter response. " +
                            "Raw: " + json);
        }

        int valueStart = keyIndex + searchKey.length();
        int valueEnd = json.indexOf(',', valueStart);
        if (valueEnd == -1) {
            valueEnd = json.indexOf('}', valueStart);
        }

        if (valueEnd == -1) {
            throw new RateFetchException(
                    "Could not parse rate value from Frankfurter response. Raw: " + json);
        }

        String rateStr = json.substring(valueStart, valueEnd).trim();
        log.info("Parsed rate string: '{}'", rateStr);

        try {
            BigDecimal rate = new BigDecimal(rateStr);
            log.info("Successfully parsed rate {}/{} = {}", sourceCurrency, targetCurrency, rate);
            return rate;
        } catch (NumberFormatException e) {
            throw new RateFetchException(
                    "Could not convert rate value '" + rateStr + "' to BigDecimal. " +
                            "Raw response: " + json);
        }
    }

}