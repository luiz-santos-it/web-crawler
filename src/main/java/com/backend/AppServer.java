package com.backend;

import com.backend.controller.CrawlController;
import com.backend.service.ICrawlService;
import spark.Spark;

import java.util.logging.Logger;

public class AppServer {
    private static final Logger LOGGER = Logger.getLogger(AppServer.class.getName());
    private final ICrawlService crawlService;
    private final int port;

    public AppServer(ICrawlService crawlService, int port) {
        this.crawlService = crawlService;
        this.port = port;
    }

    public void start() {
        Spark.port(port);

        CrawlController.initializeRoutes(crawlService);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down the application...");
            crawlService.shutdown();
            Spark.stop();
            LOGGER.info("Shutdown complete.");
        }));

        LOGGER.info("Application started on port " + port);
    }
}
