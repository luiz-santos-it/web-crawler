package com.backend.unit;

import com.backend.model.ISearchOperation;
import com.backend.model.SearchStatus;
import com.backend.service.CrawlConfig;
import com.backend.service.CrawlService;
import com.backend.service.ICircuitBreaker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrawlServiceTest {

    private static final String BASE_URL = "https://www.youtube.com/";
    private static final int MAX_RESULTS = 10;
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_QUEUE_SIZE = 1000;
    private ExecutorService executorService;
    private CrawlConfig config;
    private CrawlService.Dependencies dependencies;
    private ICircuitBreaker circuitBreaker;
    private CrawlService crawlService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newSingleThreadExecutor();
        config = new CrawlConfig(BASE_URL, MAX_RESULTS, MAX_RETRIES, TIMEOUT_MS, MAX_QUEUE_SIZE);
        dependencies = mock(CrawlService.Dependencies.class);
        circuitBreaker = mock(ICircuitBreaker.class);
        crawlService = new CrawlService(executorService, config, circuitBreaker, dependencies);
    }

    @Test
    void testStartSearchValidKeyword() throws Exception {
        when(dependencies.getBodyTextFromUrl(any(URL.class), anyInt())).thenReturn("security content");

        String searchId = crawlService.startSearch("security");

        executorService.invokeAll(List.of(() -> null));

        ISearchOperation operation = crawlService.getSearchOperation(searchId);

        assertNotNull(operation);
        Assertions.assertEquals(SearchStatus.DONE, operation.getStatus());
        assertFalse(operation.getUrls().isEmpty());
    }

    @Test
    public void testKeywordValidation() {
        assertThrows(IllegalArgumentException.class, () -> crawlService.startSearch("abc"));
        assertThrows(IllegalArgumentException.class, () -> crawlService.startSearch("a".repeat(33)));
        assertDoesNotThrow(() -> crawlService.startSearch("abcd"));
        assertDoesNotThrow(() -> crawlService.startSearch("a".repeat(32)));
        assertDoesNotThrow(() -> crawlService.startSearch("Security"));
    }

    @Test
    void testCircuitBreakerThresholdWithRetries() throws Exception {
        URL url = new URL(BASE_URL);
        when(dependencies.getBodyTextFromUrl(any(URL.class), anyInt())).thenThrow(new IOException("Simulated failure"));
        when(circuitBreaker.shouldSkip(eq(url), anyString())).thenReturn(false);

        String searchId = crawlService.startSearch("security");

        executorService.invokeAll(List.of(() -> null));

        ISearchOperation searchOperation = crawlService.getSearchOperation(searchId);

        verify(circuitBreaker, times(MAX_RETRIES + 1)).recordFailure(eq(url));
        verify(dependencies, times(MAX_RETRIES + 1)).getBodyTextFromUrl(eq(url), eq(TIMEOUT_MS));
        assertEquals(SearchStatus.DONE, searchOperation.getStatus());
    }

    @Test
    void testMaxResultsLimit() throws Exception {
        when(dependencies.getBodyTextFromUrl(any(URL.class), anyInt())).thenReturn(
                "security <a href=\"https://www.youtube.com/page1.html\">Link 1</a>" +
                        "<a href=\"https://www.youtube.com/page2.html\">Link 2</a>" +
                        "<a href=\"https://www.youtube.com/page3.html\">Link 3</a>" +
                        "<a href=\"https://www.youtube.com/page4.html\">Link 4</a>" +
                        "<a href=\"https://www.youtube.com/page5.html\">Link 5</a>" +
                        "<a href=\"https://www.youtube.com/page6.html\">Link 6</a>" +
                        "<a href=\"https://www.youtube.com/page7.html\">Link 7</a>" +
                        "<a href=\"https://www.youtube.com/page8.html\">Link 8</a>" +
                        "<a href=\"https://www.youtube.com/page9.html\">Link 9</a>" +
                        "<a href=\"https://www.youtube.com/page10.html\">Link 10</a>"
        );

        String searchId = crawlService.startSearch("security");

        executorService.invokeAll(List.of(() -> null));

        ISearchOperation searchOperation = crawlService.getSearchOperation(searchId);

        assertEquals(SearchStatus.DONE, searchOperation.getStatus());
        assertEquals(MAX_RESULTS, searchOperation.getUrls().size());
    }

    @Test
    void testShutdown() {
        assertFalse(executorService.isShutdown());
        crawlService.shutdown();
        assertTrue(executorService.isShutdown());
    }

    @Test
    void testConcurrentSearches() throws Exception {
        Callable<String> task1 = () -> crawlService.startSearch("security");
        Callable<String> task2 = () -> crawlService.startSearch("privacy");

        Future<String> future1 = executorService.submit(task1);
        Future<String> future2 = executorService.submit(task2);

        executorService.invokeAll(List.of(() -> null));

        String searchId1 = future1.get();
        String searchId2 = future2.get();

        assertNotNull(searchId1);
        assertNotNull(searchId2);
        assertNotEquals(searchId1, searchId2);
    }

    @Test
    void testShutdownWithInterruption() throws InterruptedException {
        ExecutorService mockExecutorService = mock(ExecutorService.class);
        CrawlService serviceWithMockExecutor = new CrawlService(mockExecutorService, config, circuitBreaker, dependencies);

        doThrow(new InterruptedException()).when(mockExecutorService).awaitTermination(anyLong(), any(TimeUnit.class));

        serviceWithMockExecutor.shutdown();

        verify(mockExecutorService).shutdownNow();
    }
}
