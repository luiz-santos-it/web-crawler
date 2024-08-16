package com.backend.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class SearchOperation implements ISearchOperation {

    private final String id;
    private final String keyword;
    private final Set<String> visitedUrls;
    private final Set<String> urls;
    private final AtomicReference<SearchStatus> status;
    private final AtomicInteger retryCount;

    private volatile String cachedJson;

    public SearchOperation(String keyword) {
        this.id = generateId();
        this.keyword = keyword;
        this.visitedUrls = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.urls = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.status = new AtomicReference<>(SearchStatus.ACTIVE);
        this.retryCount = new AtomicInteger(0);
        this.cachedJson = buildJson();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getKeyword() {
        return keyword;
    }

    @Override
    public Set<String> getVisitedUrls() {
        return visitedUrls;
    }

    @Override
    public void addVisitedUrl(String url) {
        visitedUrls.add(url);
    }

    @Override
    public Set<String> getUrls() {
        return urls;
    }

    @Override
    public void addUrls(List<String> urls) {
        this.urls.addAll(urls);
        updateCachedJson();
    }

    @Override
    public SearchStatus getStatus() {
        return status.get();
    }

    @Override
    public void setStatus(SearchStatus status) {
        this.status.set(status);
        updateCachedJson();
    }

    @Override
    public int getRetryCount() {
        return retryCount.get();
    }

    @Override
    public void incrementRetryCount() {
        retryCount.incrementAndGet();
    }

    @Override
    public String toString() {
        return cachedJson;
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void updateCachedJson() {
        cachedJson = buildJson();
    }

    private String buildJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\": \"").append(id).append("\",");
        sb.append("\"status\": \"").append(status.get().name().toLowerCase()).append("\",");
        sb.append("\"urls\": [");

        boolean first = true;
        for (String url : urls) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(url).append("\"");
            first = false;
        }

        sb.append("]");
        sb.append("}");
        return sb.toString();
    }
}