# 🔁 Resilience Retry Deduplication & Clean Layering (v0.7)

> **TL;DR**: Removed duplicate `@Retry` annotation from `MatchingService.matchDriverForRide()` to resolve nested retry exponential multiplication ($3 \times 3 = 9$ attempts), ensuring clean single-layer resilience separation between HTTP client calls and Kafka consumer error handling.

---

## 1. Problem Overview & Root Cause Analysis

During a downstream service outage (e.g. `location-service` being offline), consuming a `ride.requested` event in `matching-service` resulted in unexpected log behavior:
1. **Initial Log Burst (9 attempts)**: The log printed `🔁 Location Service GET attempt` 9 times on the first consumer attempt instead of the configured `max-attempts=3`.
2. **Subsequent Log Bursts (6 attempts)**: On subsequent retries, the attempt count dropped to 5 or 6 logs before tripping the Resilience4j Circuit Breaker to `OPEN`.
3. **Endless Repetition**: The entire sequence repeated continuously in an infinite loop.

### Root Cause: Duplicate / Nested `@Retry` Annotations

Both the service orchestration method and the HTTP resilient client method were annotated with `@Retry(name = "locationServiceRetry")`:

* **Outer Layer**: `MatchingService.matchDriverForRide(event)` $\rightarrow$ `@Retry(name = "locationServiceRetry")` (3 max attempts)
* **Inner Layer**: `LocationServiceResilientClient.getNearbyDrivers(...)` $\rightarrow$ `@Retry(name = "locationServiceRetry")` (3 max attempts)

```
[Kafka Consumer]
       │
       ▼
MatchingService.matchDriverForRide() [@Retry: 3 max attempts]
       │
       ├── (Attempt 1) ──► LocationServiceResilientClient.getNearbyDrivers() [@Retry: 3 max attempts]
       │                         ├── HTTP Call #1 (Fails)
       │                         ├── HTTP Call #2 (Fails)
       │                         └── HTTP Call #3 (Fails) ──► Throws Exception
       │
       ├── (Attempt 2) ──► LocationServiceResilientClient.getNearbyDrivers() [@Retry: 3 max attempts]
       │                         ├── HTTP Call #4 (Fails)
       │                         ├── HTTP Call #5 (Fails - CircuitBreaker TRIPS OPEN)
       │                         └── HTTP Call #6 (Blocked by Open CircuitBreaker)
       │
       └── (Attempt 3) ──► LocationServiceResilientClient.getNearbyDrivers() [@Retry: 3 max attempts]
                                 └── Calls #7, #8, #9 (Blocked by Open CircuitBreaker)
```

**Multiplication Effect:**
* 3 outer method retries $\times$ 3 inner client retries = **9 total execution attempts**.
* After 5 failed calls (`minimum-number-of-calls=5`), the Resilience4j Circuit Breaker tripped to `OPEN`, causing subsequent calls to be rejected fast with `CallNotPermittedException`.
* Unhandled exceptions propagated up to Spring Kafka's `DefaultErrorHandler`, triggering consumer-level re-polls and infinite loop retries.

---

## 2. Technical Solution

Removed `@Retry(name = "locationServiceRetry")` from [`MatchingService.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/service/MatchingService.java).

### Architectural Layering Rules:

| Layer | Component | Annotation / Mechanism | Responsibility |
| :--- | :--- | :--- | :--- |
| **HTTP Client Resilience** | [`LocationServiceResilientClient.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/client/LocationServiceResilientClient.java) | `@Retry(name = "locationServiceRetry")`<br/>`@CircuitBreaker(name = "locationServiceCircuitBreaker")` | Manages transient HTTP network retries & circuit breaker protection for downstream Feign REST calls. |
| **Business Logic Orchestration** | [`MatchingService.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/service/MatchingService.java) | *No Retry Annotations* | Pure business orchestration (scoring drivers, invoking atomic claims, emitting Kafka events). |
| **Kafka Event Consumer Retry** | [`KafkaConsumerConfig.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/config/KafkaConsumerConfig.java) | `DefaultErrorHandler`<br/>`ExponentialBackOffWithMaxRetries(3)` | Handles event-level retries (1s $\rightarrow$ 2s $\rightarrow$ 4s) and routes exhausted messages to `ride.requested-dlt`. |

---

## 3. Impact & Verification

1. **Clean Attempt Execution**: Calling `resilientClient.getNearbyDrivers()` now attempts exactly 3 HTTP retries when downstream is failing, matching the `max-attempts=3` configuration.
2. **Circuit Breaker Hygiene**: Circuit breaker state transitions (`CLOSED` $\rightarrow$ `OPEN` $\rightarrow$ `HALF_OPEN`) are clean and deterministic without aspect interference.
3. **No Unintended Side Effects**: Business logic and event processing contracts remain intact.

---

## 4. Key File Modifications

* ✂️ **Removed Duplicate `@Retry`**: [`MatchingService.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/service/MatchingService.java#L42)
* 🛡️ **Preserved Resilient Client**: [`LocationServiceResilientClient.java`](../../matching-service/src/main/java/com/rideshare/matchingservice/client/LocationServiceResilientClient.java#L21)
