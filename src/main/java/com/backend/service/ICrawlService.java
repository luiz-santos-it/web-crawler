package com.backend.service;
import com.backend.model.ISearchOperation;

public interface ICrawlService {
    String startSearch(String keyword);
    ISearchOperation getSearchOperation(String id);
    void shutdown();
}



