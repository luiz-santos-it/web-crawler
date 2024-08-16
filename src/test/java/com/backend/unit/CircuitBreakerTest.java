package com.backend.unit;

import com.backend.service.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final long TIMEOUT_MILLIS = 2000;

    private CircuitBreaker circuitBreaker;
    private URL testUrl;
    private URL anotherUrl;

    @BeforeEach
    void setUp() throws Exception {
        circuitBreaker = new CircuitBreaker(CIRCUIT_BREAKER_THRESHOLD, TIMEOUT_MILLIS);
        testUrl = new URL("http://example.com");
        anotherUrl = new URL("http://another-example.com");
    }

    @Test
    void testCircuitBreakerActivatesAfterThreshold() {
        for (int i = 0; i < CIRCUIT_BREAKER_THRESHOLD - 1; i++) {
            circuitBreaker.recordFailure(testUrl);
            assertFalse(circuitBreaker.shouldSkip(testUrl, "operationId"));
        }

        circuitBreaker.recordFailure(testUrl);
        assertTrue(circuitBreaker.shouldSkip(testUrl, "operationId"));
    }

    @Test
    void testCircuitBreakerDoesNotSkipBeforeThreshold() {
        for (int i = 0; i < CIRCUIT_BREAKER_THRESHOLD - 1; i++) {
            circuitBreaker.recordFailure(testUrl);
            assertFalse(circuitBreaker.shouldSkip(testUrl, "operationId"));
        }
    }

    @Test
    void testCircuitBreakerResetsAfterTimeout() throws InterruptedException {
        for (int i = 0; i < CIRCUIT_BREAKER_THRESHOLD; i++) {
            circuitBreaker.recordFailure(testUrl);
        }

        assertTrue(circuitBreaker.shouldSkip(testUrl, "operationId"));
        Thread.sleep(TIMEOUT_MILLIS + 500);

        assertFalse(circuitBreaker.shouldSkip(testUrl, "operationId"));
    }

    @Test
    void testCircuitBreakerDoesNotResetBeforeTimeout() throws InterruptedException {
        for (int i = 0; i < CIRCUIT_BREAKER_THRESHOLD; i++) {
            circuitBreaker.recordFailure(testUrl);
        }

        assertTrue(circuitBreaker.shouldSkip(testUrl, "operationId"));

        Thread.sleep(TIMEOUT_MILLIS / 2);

        assertTrue(circuitBreaker.shouldSkip(testUrl, "operationId"));
    }

    @Test
    void testCircuitBreakerWithMultipleUrls() throws InterruptedException {
        for (int i = 0; i < CIRCUIT_BREAKER_THRESHOLD; i++) {
            circuitBreaker.recordFailure(testUrl);
        }

        assertTrue(circuitBreaker.shouldSkip(testUrl, "operationId"));

        for (int i = 0; i < CIRCUIT_BREAKER_THRESHOLD; i++) {
            circuitBreaker.recordFailure(anotherUrl);
        }

        assertTrue(circuitBreaker.shouldSkip(anotherUrl, "operationId"));

        Thread.sleep(TIMEOUT_MILLIS + 500);

        assertFalse(circuitBreaker.shouldSkip(testUrl, "operationId"));
        assertFalse(circuitBreaker.shouldSkip(anotherUrl, "operationId"));
    }

    @Test
    void testCircuitBreakerNoFailureRecorded() {
        assertFalse(circuitBreaker.shouldSkip(testUrl, "operationId"));
    }
}
