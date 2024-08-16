package com.backend.controller;


import com.backend.model.ISearchOperation;
import com.backend.service.ICrawlService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.logging.Level;
import java.util.logging.Logger;

import static spark.Spark.get;
import static spark.Spark.post;


public class CrawlController {
    private static final Logger LOGGER = Logger.getLogger(CrawlController.class.getName());
    private static ICrawlService crawlService;
    private static final Gson gson = new Gson();

    public static void initializeRoutes(ICrawlService crawlService) {
        CrawlController.crawlService = crawlService;
        post("/crawl", handleCrawlRequest);
        get("/crawl/:id", handleGetRequest);
    }

    public static Route handleCrawlRequest = (Request req, Response res) -> {
        CrawlRequest crawlRequest = extractKeywordFromBody(req.body());
        if (crawlRequest == null || crawlRequest.getKeyword() == null) {
            LOGGER.log(Level.WARNING, "Invalid JSON format or missing keyword");
            res.status(400);
            res.type("application/json");
            return gson.toJson(createErrorResponse("Invalid JSON format or missing keyword"));
        }

        String keyword = crawlRequest.getKeyword();
        LOGGER.log(Level.INFO, "Received crawl request for keyword: {0}", keyword);

        try {
            String searchId = crawlService.startSearch(keyword);
            res.type("application/json");
            JsonObject jsonResponse = new JsonObject();
            jsonResponse.addProperty("id", searchId);
            return gson.toJson(jsonResponse);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Keyword validation failed: {0}", keyword);
            res.status(400);
            res.type("application/json");
            return gson.toJson(createErrorResponse(e.getMessage()));
        }
    };

    public static Route handleGetRequest = (Request req, Response res) -> {
        String id = req.params(":id");
        ISearchOperation searchOperation = crawlService.getSearchOperation(id);

        if (searchOperation == null) {
            LOGGER.log(Level.WARNING, "Search not found for ID: {0}", id);
            res.status(404);
            res.type("application/json");
            return gson.toJson(createErrorResponse("Search not found"));
        }

        LOGGER.log(Level.INFO, "Returning search result for ID: {0}", id);
        res.type("application/json");
        return searchOperation.toString();
    };

    private static CrawlRequest extractKeywordFromBody(String body) {
        try {
            return gson.fromJson(body, CrawlRequest.class);
        } catch (JsonSyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to parse JSON body", e);
            return null;
        }
    }

    private static JsonObject createErrorResponse(String message) {
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("error", message);
        return errorResponse;
    }

    private static class CrawlRequest {
        private String keyword;

        public String getKeyword() {
            return keyword;
        }
    }
}