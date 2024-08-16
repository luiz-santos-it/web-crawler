package com.backend;

import com.backend.service.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        String baseUrl = System.getenv("BASE_URL");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "4567"));

        if (baseUrl == null || baseUrl.isEmpty()) {
            LOGGER.severe("BASE_URL environment variable is not set.");
            System.exit(1);
        }

        final int maxResults = 100;
        final int maxRetries = 3;
        final int timeout = 5000;
        final int maxQueueSize = 50000;
        final int circuitBreakerThreshold = 5;

        CrawlConfig config = new CrawlConfig(baseUrl, maxResults, maxRetries, timeout, maxQueueSize);

        ExecutorService executorService = Executors.newCachedThreadPool();
        ICircuitBreaker circuitBreaker = new CircuitBreaker(circuitBreakerThreshold, TimeUnit.MINUTES.toMillis(10));
        ICrawlService crawlService = new CrawlService(executorService, config, circuitBreaker);

        AppServer server = new AppServer(crawlService, port);
        server.start();
    }
}
