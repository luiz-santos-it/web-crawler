package com.backend.service;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CircuitBreaker implements ICircuitBreaker {
    private static final Logger LOGGER = Logger.getLogger(CircuitBreaker.class.getName());
    private final ConcurrentHashMap<String, FailureRecord> failureCountMap = new ConcurrentHashMap<>();
    private final int threshold;
    private final long timeoutMillis;

    public CircuitBreaker(int threshold, long timeoutMillis) {
        this.threshold = threshold;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public boolean shouldSkip(URL url, String operationId) {
        String urlKey = url.toString();
        FailureRecord record = failureCountMap.get(urlKey);

        if (record != null) {
            if (record.failureCount >= threshold) {
                long elapsedTime = System.currentTimeMillis() - record.lastFailureTime;

                if (elapsedTime < timeoutMillis) {
                    logCircuitBreakerActive(url, operationId, record.failureCount, elapsedTime);
                    return true;
                } else {
                    failureCountMap.remove(urlKey);
                }
            }
        }

        return false;
    }

    @Override
    public void recordFailure(URL url) {
        String urlKey = url.toString();
        failureCountMap.merge(urlKey, new FailureRecord(1, System.currentTimeMillis()), (existing, newValue) -> new FailureRecord(existing.failureCount + 1, System.currentTimeMillis()));
        LOGGER.log(Level.WARNING, "Incremented failure count for URL: {0}.", url);
    }

    private void logCircuitBreakerActive(URL url, String operationId, int failureCount, long elapsedTime) {
        LOGGER.log(Level.WARNING, "Circuit breaker active for URL: {0} for operation ID: {1}. Skipping URL. Failure count: {2}. Time since last failure: {3}ms.", new Object[]{url, operationId, failureCount, elapsedTime});
    }

    private static class FailureRecord {
        final int failureCount;
        final long lastFailureTime;

        FailureRecord(int failureCount, long lastFailureTime) {
            this.failureCount = failureCount;
            this.lastFailureTime = lastFailureTime;
        }
    }
}
