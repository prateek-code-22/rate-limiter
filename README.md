# Spring Boot Redis Rate Limiter (Token Bucket)

A distributed rate-limiting solution built with **Spring Boot** and **Redis**, implementing the **Token Bucket Algorithm**. This project functions as an API Gateway component that regulates traffic based on client identity (IP Address).

## 🚀 Overview

This project demonstrates a robust, scalable rate limiter that:
- Uses **Redis Lua Scripts** to ensure atomicity in distributed environments.
- Implements the **Token Bucket** algorithm for smooth traffic shaping.
- Built on top of **Spring Cloud Gateway** for high-performance non-blocking I/O.
- Provides fallback mechanisms for identifying clients through `X-Forwarded-For` headers.

---

## 🏗 Project Structure

```text
rate-limiter/
├── src/main/java/com/app/ratelimiter/
│   ├── config/               # Configuration (Redis, Gateway, Properties)
│   ├── filter/               # Gateway Filter (Rate limiting logic)
│   ├── service/              # Redis interaction & Lua script execution
│   ├── controller/           # Health and status monitoring
│   └── RateLimiterApplication.java
├── src/main/resources/
│   └── application.properties # System configuration
├── mockServer.py              # Mock backend for testing
├── quick_test.sh              # Performance / Functional test script
└── build.gradle               # Dependency management
```

---

## 🛠 Tech Stack

- **Java 21**
- **Spring Boot 3.4.0**
- **Spring Cloud Gateway**
- **Redis (Jedis)**
- **Lua** (for atomic operations)
- **Gradle**

---

## ⚙️ Configuration

Configure the rate limiter in `src/main/resources/application.properties`:

```properties
# Redis Configuration
spring.redis.host=localhost
spring.redis.port=6379

# Rate Limiter Tunables
rate-limiter.capacity=10       # Max tokens in the bucket
rate-limiter.refill-rate=1     # Tokens added per second
rate-limiter.timeout=5000      # Request timeout
rate-limiter.api-server-url=http://localhost:8081 # Target Backend
```

---

## 🧠 How it Works (Token Bucket Algorithm)

1. **Request Interception**: The `TokenBucketRateLimiterFilter` intercepts every incoming request.
2. **Client Identification**: It extracts the client's IP from the `X-Forwarded-For` header or direct remote address.
3. **Atomic Check**: It executes a Lua script on Redis:
    - Calculates the tokens to refill based on the time elapsed since the last request.
    - Checks if at least 1 token is available.
    - If available, decrements the token and returns `1` (Allowed).
    - If empty, returns `0` (Rate Limited).
4. **Response**: 
    - **Success (200 OK)**: Forwards the request to the backend and adds `X-RateLimit` headers.
    - **Failure (429 Too Many Requests)**: Returns a JSON error response.

---

## 🚦 Getting Started

### 1. Prerequisites
- Redis server running on `localhost:6379`.
- JDK 17 or higher.

### 2. Run the Mock Backend
Since the rate limiter acts as a gateway, you need a backend server to forward requests to:
```bash
python mockServer.py
```

### 3. Run the Rate Limiter
```bash
./gradlew bootRun
```

### 4. Test the API
Use the provided script to simulate traffic:
```bash
bash quick_test.sh
```
Or use `curl`:
```bash
curl -v http://localhost:8080/api/resource
```

---

## 📊 Monitoring & Health

The gateway provides internal endpoints for monitoring:
- `GET /gateway/health`: Check if the gateway is up.
- `GET /gateway/rate-limit/status`: View current token status for your IP.

## 📡 Monitoring Headers
The application injects the following headers into every redirected response:
- `X-RateLimit-Limit`: Maximum bucket capacity.
- `X-RateLimit-Remaining`: Tokens currently available for the client.
