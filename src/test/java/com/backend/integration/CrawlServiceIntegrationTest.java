package com.backend.integration;

import com.backend.controller.CrawlController;
import com.backend.service.CrawlConfig;
import com.backend.service.CrawlService;
import com.backend.service.ICircuitBreaker;
import com.backend.service.CircuitBreaker;
import com.backend.service.ICrawlService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.Spark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrawlServiceIntegrationTest {

    private static final Gson GSON = new Gson();
    private static final int PORT = 4568;
    private static final String SERVER_URL = "http://localhost:" + PORT;
    private static final String BASE_URL = "https://www.youtube.com/";

    private static final int MAX_RESULTS = 100;
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT_MILLISECONDS = 5000;
    private static final int MAX_QUEUE_SIZE = 20000;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final int CIRCUIT_BREAKER_TIMEOUT_MILLISECONDS = 600000;
    private static final int SERVER_STARTUP_DELAY_MILLISECONDS = 2000;
    private static final int SEARCH_COMPLETION_TIMEOUT_MILLISECONDS = 30000;
    private static final int SLEEP_INTERVAL_MILLISECONDS = 1000;

    private static ICrawlService crawlService;

    @BeforeAll
    public static void initializeTestEnvironment() throws Exception {
        stopSparkServer();
        startLocalServer();
        waitForServerStartup();
    }

    @AfterAll
    public static void cleanUpTestEnvironment() {
        stopSparkServer();
    }

    private static void startLocalServer() {
        CrawlConfig config = new CrawlConfig(
                BASE_URL,
                MAX_RESULTS,
                MAX_RETRIES,
                TIMEOUT_MILLISECONDS,
                MAX_QUEUE_SIZE
        );
        ExecutorService executorService = Executors.newCachedThreadPool();
        ICircuitBreaker circuitBreaker = new CircuitBreaker(CIRCUIT_BREAKER_THRESHOLD, CIRCUIT_BREAKER_TIMEOUT_MILLISECONDS);

        crawlService = new CrawlService(executorService, config, circuitBreaker);

        Spark.port(PORT);
        CrawlController.initializeRoutes(crawlService);
    }

    private static void stopSparkServer() {
        Spark.stop();
    }

    private static void waitForServerStartup() throws InterruptedException {
        Thread.sleep(SERVER_STARTUP_DELAY_MILLISECONDS);
    }

    @Test
    public void shouldCompleteBasicSearchOperationSuccessfully() throws Exception {
        String searchId = startSearch("linux");
        assertNotNull(searchId);

        String status = waitForSearchCompletion(searchId);
        assertEquals("done", status);
    }

    @Test
    public void shouldReturnErrorForInvalidKeyword() throws Exception {
        String response = startSearchWithExpectedFailure("abc");
        assertEquals("Keyword must be between 4 and 32 characters", response);

        response = startSearchWithExpectedFailure("a".repeat(33));
        assertEquals("Keyword must be between 4 and 32 characters", response);
    }

    @Test
    public void shouldHandleConcurrentSearchOperations() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);

        Runnable searchTask = () -> {
            try {
                String searchId = startSearch("test");
                assertNotNull(searchId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        for (int i = 0; i < 5; i++) {
            executor.submit(searchTask);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(SEARCH_COMPLETION_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldReturnPartialResultsDuringActiveSearch() throws Exception {
        String searchId = startSearch("linux");

        boolean partialResultsFound = false;

        do {
            String status = getSearchStatus(searchId);

            if (status.equals("active")) {
                JsonObject partialResults = getSearchResults(searchId);
                if (partialResults.has("urls") && partialResults.getAsJsonArray("urls").size() > 0) {
                    partialResultsFound = true;
                }
            } else if (status.equals("done")) {
                break;
            }

            Thread.sleep(SLEEP_INTERVAL_MILLISECONDS);

        } while (getSearchStatus(searchId).equals("active"));

        assertTrue(partialResultsFound, "Partial results were found during active status");
        assertEquals("done", getSearchStatus(searchId), "Expected the search to be completed ('done')");
    }

    private String startSearch(String keyword) throws Exception {
        HttpURLConnection connection = openConnection("/crawl", "POST");

        JsonObject requestBody = createSearchRequestBody(keyword);
        connection.getOutputStream().write(GSON.toJson(requestBody).getBytes());

        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());

        return extractSearchIdFromResponse(connection);
    }

    private String startSearchWithExpectedFailure(String keyword) throws Exception {
        HttpURLConnection connection = openConnection("/crawl", "POST");

        JsonObject requestBody = createSearchRequestBody(keyword);
        connection.getOutputStream().write(GSON.toJson(requestBody).getBytes());

        try {
            connection.getInputStream();
        } catch (IOException e) {
            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, connection.getResponseCode());

            String errorResponse = readErrorResponse(connection);
            JsonObject jsonResponse = GSON.fromJson(errorResponse, JsonObject.class);
            return jsonResponse.get("error").getAsString();
        }

        throw new AssertionError("Expected a 400 Bad Request, but the request was successful.");
    }


    private String readErrorResponse(HttpURLConnection connection) throws Exception {
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return response.toString();
    }

    private JsonObject createSearchRequestBody(String keyword) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("keyword", keyword);
        return requestBody;
    }

    private HttpURLConnection openConnection(String endpoint, String method) throws Exception {
        URL url = new URL(SERVER_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        return connection;
    }

    private String extractSearchIdFromResponse(HttpURLConnection connection) throws Exception {
        String response = readResponse(connection);
        JsonObject jsonResponse = GSON.fromJson(response, JsonObject.class);
        return jsonResponse.get("id").getAsString();
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return response.toString();
    }

    private String waitForSearchCompletion(String searchId) throws Exception {
        String status;
        while (true) {
            status = getSearchStatus(searchId);
            if ("done".equals(status) || "failed".equals(status)) {
                break;
            }
            try {
                Thread.sleep(SLEEP_INTERVAL_MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
        return status;
    }


    private String getSearchStatus(String searchId) throws Exception {
        HttpURLConnection connection = openConnection("/crawl/" + searchId, "GET");

        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());

        String response = readResponse(connection);
        JsonObject jsonResponse = GSON.fromJson(response, JsonObject.class);
        return jsonResponse.get("status").getAsString();
    }

    private JsonObject getSearchResults(String searchId) throws Exception {
        HttpURLConnection connection = openConnection("/crawl/" + searchId, "GET");

        assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());

        String response = readResponse(connection);
        return GSON.fromJson(response, JsonObject.class);
    }
}
