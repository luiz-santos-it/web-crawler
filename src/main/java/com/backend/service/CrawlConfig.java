package com.backend.service;

/**
 * Configuration class for the web crawler. This class encapsulates various
 * parameters that control the behavior of the crawling process.
 */
public class CrawlConfig {
    private final String baseURL;
    private final int maxResults;
    private final int maxRetries;
    private final int timeout;
    private final int maxQueueSize;

    /**
     * Constructs a new {@code CrawlConfig} with the specified configuration parameters.
     *
     * @param baseURL               the base URL from which the crawling starts. Only links within this base URL are followed.
     * @param maxResults            the maximum number of URLs to collect per search operation.
     * @param maxRetries            the maximum number of retries if a search operation fails.
     * @param timeout               the timeout (in milliseconds) for HTTP connections.
     * @param maxQueueSize          the maximum number of URLs that can be queued for crawling in a single search operation.
     */
    public CrawlConfig(String baseURL, int maxResults, int maxRetries, int timeout, int maxQueueSize) {
        this.baseURL = baseURL;
        this.maxResults = maxResults;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Returns the base URL from which the crawling starts.
     *
     * @return the base URL.
     */
    public String getBaseURL() {
        return baseURL;
    }

    /**
     * Returns the maximum number of URLs to collect per search operation.
     *
     * @return the maximum number of URLs.
     */
    public int getMaxResults() {
        return maxResults;
    }

    /**
     * Returns the maximum number of retries if a search operation fails.
     *
     * @return the maximum number of retries.
     */
    public int getMaxRetries() {
        return maxRetries;
    }


    /**
     * Returns the timeout (in milliseconds) for HTTP connections.
     *
     * @return the timeout in milliseconds.
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Returns the maximum number of URLs that can be queued for crawling in a single search operation.
     *
     * @return the maximum queue size.
     */
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

}
