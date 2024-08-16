package com.backend.service;

import com.backend.model.ISearchOperation;
import com.backend.model.SearchStatus;
import com.backend.model.SearchOperation;
import com.backend.util.HttpUtil;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CrawlService implements ICrawlService {
    private static final Logger LOGGER = Logger.getLogger(CrawlService.class.getName());
    private static final int MIN_KEYWORD_LENGTH = 4;
    private static final int MAX_KEYWORD_LENGTH = 32;
    private static final String KEYWORD_LENGTH_ERROR_MESSAGE = "Keyword must be between %d and %d characters";

    private final ConcurrentHashMap<String, ISearchOperation> searchOperations;
    private final ExecutorService executor;
    private final Dependencies dependencies;
    private final CrawlConfig config;
    private final ICircuitBreaker circuitBreaker;

    public CrawlService(ExecutorService executor, CrawlConfig config, ICircuitBreaker circuitBreaker) {
        this(executor, config, circuitBreaker, new Dependencies());
    }

    public CrawlService(ExecutorService executor, CrawlConfig config, ICircuitBreaker circuitBreaker, Dependencies dependencies) {
        this.executor = executor;
        this.searchOperations = new ConcurrentHashMap<>();
        this.config = config;
        this.dependencies = dependencies;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public String startSearch(String keyword) {
        validateKeyword(keyword);
        ISearchOperation searchOperation = new SearchOperation(keyword);
        searchOperations.put(searchOperation.getId(), searchOperation);
        LOGGER.log(Level.INFO, "Started search operation with ID: {0}", searchOperation.getId());

        executor.submit(() -> executeSearch(searchOperation));

        return searchOperation.getId();
    }

    private void executeSearch(ISearchOperation searchOperation) {
        try {
            startCrawling(searchOperation);
            searchOperation.setStatus(SearchStatus.DONE);
            LOGGER.log(Level.INFO, "Search operation completed for ID: {0}", searchOperation.getId());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during search operation for ID: " + searchOperation.getId(), e);
            searchOperation.setStatus(SearchStatus.FAILED);
        }
    }

    @Override
    public ISearchOperation getSearchOperation(String id) {
        return searchOperations.get(id);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void startCrawling(ISearchOperation searchOperation) throws Exception {
        Queue<URL> queue = initializeQueue(searchOperation);

        while (!queue.isEmpty() && searchOperation.getUrls().size() < config.getMaxResults()) {
            URL currentUrl = queue.poll();
            try {
                if (circuitBreaker.shouldSkip(currentUrl, searchOperation.getId())) {
                    continue;
                }

                processUrl(currentUrl, searchOperation, queue);
            } catch (Exception e) {
                circuitBreaker.recordFailure(currentUrl);
                if (searchOperation.getRetryCount() < config.getMaxRetries()) {
                    searchOperation.incrementRetryCount();
                    queue.add(currentUrl);
                }
            }
        }

        if (searchOperation.getStatus() != SearchStatus.FAILED) {
            searchOperation.setStatus(SearchStatus.DONE);
        }
    }

    private Queue<URL> initializeQueue(ISearchOperation searchOperation) throws MalformedURLException {
        Queue<URL> queue = new ConcurrentLinkedQueue<>();
        URL baseUrl = new URL(config.getBaseURL());
        searchOperation.addVisitedUrl(normalizeUrl(baseUrl));
        queue.add(baseUrl);
        return queue;
    }

    private void processUrl(URL currentUrl, ISearchOperation searchOperation, Queue<URL> queue) throws Exception {
        String normalizedUrl = normalizeUrl(currentUrl);

        String bodyText = dependencies.getBodyTextFromUrl(currentUrl, config.getTimeout());
        if (bodyText.toLowerCase().contains(searchOperation.getKeyword().toLowerCase())) {
            handleKeywordFound(searchOperation, normalizedUrl);
        }

        if (searchOperation.getVisitedUrls().size() < config.getMaxQueueSize()) {
            extractAndQueueLinks(bodyText, currentUrl, queue, searchOperation);
        } else {
            LOGGER.log(Level.WARNING, "Queue size limit reached after processing body text. Skipping further link extraction for operation ID: {0}.", searchOperation.getId());
        }
    }

    private void handleKeywordFound(ISearchOperation searchOperation, String normalizedUrl) {
        LOGGER.log(Level.INFO, "Keyword \"{0}\" found in URL: {1}", new Object[]{searchOperation.getKeyword(), normalizedUrl});
        searchOperation.addUrls(List.of(normalizedUrl));
        if (searchOperation.getUrls().size() >= config.getMaxResults()) {
            LOGGER.log(Level.INFO, "Reached max results limit for search operation ID: {0}", searchOperation.getId());
        }
    }

    private void extractAndQueueLinks(String bodyText, URL currentUrl, Queue<URL> queue, ISearchOperation searchOperation) throws MalformedURLException {
        Pattern pattern = Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href\\s*=\\s*[\'\\\"](.*?)[\'\\\"]", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(bodyText);
        while (matcher.find() && searchOperation.getUrls().size() < config.getMaxResults() && searchOperation.getVisitedUrls().size() < config.getMaxQueueSize()) {
            String link = matcher.group(1);
            try {
                URL newUrl = new URL(currentUrl, link);
                String normalizedNewUrl = normalizeUrl(newUrl);
                if (newUrl.getHost().equals(currentUrl.getHost()) && !searchOperation.getVisitedUrls().contains(normalizedNewUrl)) {
                    searchOperation.addVisitedUrl(normalizedNewUrl);
                    queue.add(newUrl);
                }
            } catch (MalformedURLException e) {
                LOGGER.log(Level.WARNING, "Malformed URL found: {0}", link);
            }
        }
    }

    private String normalizeUrl(URL url) {
        try {
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getPath()).toString();
        } catch (MalformedURLException e) {
            LOGGER.log(Level.WARNING, "Error normalizing URL: {0}", url);
            return url.toString();
        }
    }

    private void validateKeyword(String keyword) {
        if (keyword.length() < MIN_KEYWORD_LENGTH || keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException(String.format(KEYWORD_LENGTH_ERROR_MESSAGE, MIN_KEYWORD_LENGTH, MAX_KEYWORD_LENGTH));
        }
    }

    public static class Dependencies {
        public String getBodyTextFromUrl(URL url, int timeout) throws IOException {
            return HttpUtil.getBodyTextFromUrl(url, timeout);
        }
    }
}
