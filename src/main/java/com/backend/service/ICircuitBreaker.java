package com.backend.service;

import java.net.URL;

public interface ICircuitBreaker {
    boolean shouldSkip(URL url, String operationId);
    void recordFailure(URL url);
}
