package com.backend.model;

import java.util.List;
import java.util.Set;

public interface ISearchOperation {
    String getId();
    String getKeyword();
    Set<String> getVisitedUrls();
    void addVisitedUrl(String url);
    Set<String> getUrls();
    void addUrls(List<String> urls);
    SearchStatus getStatus();
    void setStatus(SearchStatus status);
    int getRetryCount();
    void incrementRetryCount();
}
