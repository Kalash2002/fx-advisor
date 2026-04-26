//package com.fxadvisor.rate;
//
//import com.fxadvisor.core.exception.RateFetchException;
//import com.fxadvisor.rate.client.FrankfurterClient;
//import org.junit.jupiter.api.Test;
//
//import java.math.BigDecimal;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * Tests for FrankfurterClient.
// *
// * Integration tests (calls real API) are marked clearly.
// * They require internet access and are skipped in CI with -DskipTests.
// *
// * INTERVIEW: When should you use real API calls in tests vs mocks?
// * Real API (integration) tests verify the external contract hasn't changed
// * (new field, removed field, different error format). Mock tests verify
// * your code's behaviour in isolation. Best practice: both. Mock tests run
// * in every build; integration tests run nightly or in a dedicated stage.
// */
//class FrankfurterClientTest {
//
//    // Real client — these tests call the live Frankfurter API
//    private final FrankfurterClient client = new FrankfurterClient();
//
//    @Test
//    void shouldReturnPositiveRateForUsdToInr() {
//        BigDecimal rate = client.getRate("USD", "INR");
//        assertNotNull(rate);
//        assertTrue(rate.compareTo(BigDecimal.ZERO) > 0,
//                "USD/INR rate must be positive, got: " + rate);
//        // Sanity range check — USD/INR has historically been between 60 and 120
//        assertTrue(rate.compareTo(new BigDecimal("60")) > 0,
//                "USD/INR rate suspiciously low: " + rate);
//        assertTrue(rate.compareTo(new BigDecimal("120")) < 0,
//                "USD/INR rate suspiciously high: " + rate);
//    }
//
//    @Test
//    void shouldReturnRateForEurToUsd() {
//        BigDecimal rate = client.getRate("EUR", "USD");
//        assertNotNull(rate);
//        assertTrue(rate.compareTo(BigDecimal.ZERO) > 0);
//    }
//
//    @Test
//    void shouldThrowRateFetchExceptionForInvalidCurrency() {
//        RateFetchException ex = assertThrows(RateFetchException.class,
//                () -> client.getRate("USD", "INVALID"));
//        assertEquals("RATE_FETCH_FAILED", ex.getErrorCode());
//    }
//
//    @Test
//    void shouldThrowRateFetchExceptionForSameCurrencyPair() {
//        // Frankfurter returns an empty rates map for same-currency requests
//        RateFetchException ex = assertThrows(RateFetchException.class,
//                () -> client.getRate("USD", "USD"));
//        assertEquals("RATE_FETCH_FAILED", ex.getErrorCode());
//    }
//}
