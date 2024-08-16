
# Web Crawler Project

## Overview

This project is a web crawler designed to search for a user-defined keyword across a specific website and return a list of URLs where the keyword is found. The project adheres to software engineering best practices, including clean code principles, modular design, and comprehensive testing. The crawler is exposed via a REST API for easy interaction through HTTP requests.

## Project Structure

The project follows a standard maven project structure, ensuring a clear separation between the main application code, tests, and resources:

```
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │           └── backend
│   │   │               ├── controller
│   │   │               │   └── CrawlController.java
│   │   │               ├── model
│   │   │               │   ├── ISearchOperation.java
│   │   │               │   ├── SearchOperation.java
│   │   │               │   └── SearchStatus.java
│   │   │               ├── service
│   │   │               │   ├── CircuitBreaker.java
│   │   │               │   ├── CrawlConfig.java
│   │   │               │   ├── CrawlService.java
│   │   │               │   ├── ICircuitBreaker.java
│   │   │               │   └── ICrawlService.java
│   │   │               └── util
│   │   │                   └── HttpUtil.java
│   └── test
│       ├── java
│       │   └── com
│       │           └── backend
│       │               ├── integration
│       │               │   └── CrawlServiceIntegrationTest.java
│       │               └── unit
│       │                   ├── CircuitBreakerTest.java
│       │                   └── CrawlServiceTest.java
├── pom.xml
└── Dockerfile
```

### Key Components

- **Controller (`CrawlController.java`)**: Handles HTTP requests and routes them to the appropriate service methods.
- **Model**: Defines the core data structures, including `SearchOperation` and `SearchStatus`.
- **Service (`CrawlService.java`)**: Implements the core logic for crawling the website, searching for the keyword, and tracking search status.
- **Utility (`HttpUtil.java`)**: Contains helper methods for HTTP requests and responses.

## Execution Flow

1. **POST /crawl**: Starts a new search operation for a given keyword.
    - **Request**: The user sends a JSON payload containing the keyword.
    - **Response**: Returns a unique search ID.
    - **Keyword Constraints**: The keyword must be between 4 and 32 characters and is case-insensitive.

2. **GET /crawl/{id}**: Retrieves the status and results of the search operation.
    - **Response**: Provides the current status (`active`, `done`, `failed`) and a list of URLs where the keyword was found.

3. **Concurrent Searches**: The application supports multiple searches simultaneously using `ExecutorService`, ensuring that each search runs independently without blocking others.

4. **Circuit Breaker to Handle Failures**: Uses a Circuit Breaker pattern to manage failures like timeouts or unreachable URLs.

5. **Return Results**: Results are available throughout the search via the GET endpoint.

### API Endpoints

- **POST /crawl**
    - **Request**:
      ```json
      {
        "keyword": "security"
      }
      ```
    - **Response**:
      ```json
      {
        "id": "30vbllyb"
      }
      ```

- **GET /crawl/{id}**
    - **Response**:
      ```json
      {
        "id": "30vbllyb",
        "status": "active",
        "urls": [
          "http://youtube.com/index2.html",
          "http://youtube.com/htmlm/dfg.5.html"
        ]
      }
      ```

### Configurable Parameters in `CrawlConfig`

The `CrawlConfig` class encapsulates various parameters that control the behavior of the crawling process:

- **`baseURL`**: The base URL from which the crawling starts. Only links within this base URL are followed.
- **`maxResults`**: The maximum number of URLs to collect per search operation.
- **`maxRetries`**: The maximum number of retries if a search operation fails.
- **`timeout`**: The timeout (in milliseconds) for HTTP connections.
- **`maxQueueSize`**: The maximum number of URLs that can be queued for crawling in a single search operation.

These parameters are critical for tuning the crawler's performance, managing resource usage, and ensuring robustness under different conditions.

## Development Rationale

- **Modular Design**: The code is structured to separate concerns, enhancing maintainability and extensibility.
- **Concurrency**: Managed via `ExecutorService` to handle multiple search operations simultaneously.
- **Resilience**: Implemented with the Circuit Breaker pattern to gracefully manage failures.
- **Scalability**: Configurable parameters for queue size, max results, and timeout settings allow the crawler to be tuned for various environments.

## Testing Strategy

Comprehensive testing was included to ensure the application’s reliability:

- **Unit Testing**: Focuses on individual components, using mocks to simulate dependencies.
- **Integration Testing**: Verifies the interaction between components and the correct behavior of the HTTP API.

### Concurrent Testing

Concurrent testing was implemented to achieve two key goals:

1. **Speeding Up Tests**: Running tests concurrently reduces overall testing time, providing quicker feedback during development.

2. **Ensuring Concurrency Handling**: It ensures that the application correctly manages simultaneous operations, verifying thread safety and test isolation, which helps prevent race conditions and shared resource conflicts.

## Running the Project with Docker

1. **Build the Docker Image**:
   ```bash
   docker build -t backend .
   ```

2. **Run the Docker Container**:
   ```bash
   docker run -e BASE_URL=http://youtube.com/ -p 4567:4567 --rm backend
   ```

3. **Access the API**:
    - The API is accessible at `http://localhost:4567`.
